#!/usr/bin/env python3
"""Local packaged-JAR smoke without developer connections or test accounts."""
import io
import json
import os
from pathlib import Path
import re
import subprocess
import time
import urllib.request
import urllib.error
import zipfile

ROOT = Path(__file__).resolve().parents[1]
JAR = ROOT / "target/mes-base-0.1.0-SNAPSHOT.jar"
OUT = ROOT / "target/validation"
OUT.mkdir(parents=True, exist_ok=True)
JAVA = os.environ.get("MES_JAVA_BIN", "java")
checks = []
with zipfile.ZipFile(JAR) as artifact:
    names = artifact.namelist()
    assert not any("RuntimeFixture" in n or "FoundationHttpTest" in n or "h2-" in n for n in names)
    assert not any(n.startswith("BOOT-INF/lib/mes-mes-") for n in names)
    for dependency in ("mssql-jdbc-", "mysql-connector-j-", "postgresql-", "flyway-mysql-",
                       "flyway-database-postgresql-"):
        assert any(n.startswith("BOOT-INF/lib/" + dependency) for n in names), dependency
    deferred_paths = ("dev/mesbase/mes/pro/productionmonitor/", "dev/mesbase/mes/pro/personnelanalysis/")
    deferred_types = ("SystemUserLookup.class", "UserLookupQuery.class", "SysUserDisplayExtraMapper.class",
                      "SysUserDisplayExtraMapper.xml")
    legacy_classes = ("PlatformConfiguration.class", "LegacyPlatformCondition.class", "PlatformTokenFilter.class",
                      "DemoConfiguration.class", "PlatformController.class", "PlatformMapper.class", "PlatformRows.class", "PlatformService.class",
                      "SystemUnit.class", "SystemUnit$Work.class", "SystemUnitAspect.class", "AnalyticsConfiguration.class",
                      "AnalyticsConfiguration$AnalyticsUnit.class", "AnalyticsConfiguration$AnalyticsAspect.class",
                      "BusinessMapperRegistration.class", "PlatformMapperRegistration.class", "BusinessStatementGuard.class",
                      "ErpAdapter.class", "ErpProvider.class", "ErpConnectionBinding.class", "FactoryErpConnectionResolver.class",
                      "NotConfiguredErpResolver.class", "IntegrationNotConfiguredException.class", "ErpTransactions.class",
                      "HTMLFilter.class", "ISysAttachmentService.class", "SharedDatabasePersistence.class",
                      "SharedDatabasePersistence$ScopedIdentity.class", "Enterprise.class")
    assert not any(n.endswith(legacy_classes) for n in names)
    for name in names:
        if name.startswith("BOOT-INF/lib/mes-") and name.endswith(".jar"):
            with zipfile.ZipFile(io.BytesIO(artifact.read(name))) as module:
                assert not any(n.endswith(legacy_classes) for n in module.namelist())
                assert not any(n.startswith(deferred_paths) or n.endswith(deferred_types)
                               for n in module.namelist())
    assert not any(any(path in n for path in deferred_paths) or n.endswith(deferred_types) for n in names)
checks.append({"case": "packaging-excludes-test-entities-and-H2", "passed": True})
checks.append({"case": "packaging-excludes-legacy-platform-implementation", "passed": True})
checks.append({"case": "packaging-excludes-deferred-business-features", "passed": True})
checks.append({"case": "packaging-retains-configured-jdbc-drivers-and-migrations", "passed": True})

def run(profile):
    environment = {key: value for key, value in os.environ.items() if not key.startswith(("SPRING_", "MES_", "SERVER_")) and key not in ("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS")}
    command = [
        JAVA, "-jar", str(JAR), "--server.port=0", "--server.address=127.0.0.1",
        "--spring.config.location=classpath:/application.yml",
        "--spring.profiles.active=" + (profile or "default"),
        "--logging.level.org.springframework.boot.web.servlet=DEBUG",
        "--logging.level.org.springframework.security.web.DefaultSecurityFilterChain=DEBUG",
    ]
    logfile = OUT / ("packaged-" + (profile or "default") + ".log")
    with logfile.open("w") as stream:
        process = subprocess.Popen(command, cwd=ROOT, env=environment, stdout=stream, stderr=subprocess.STDOUT)
        try:
            deadline = time.monotonic() + 30
            port = None
            while time.monotonic() < deadline:
                if process.poll() is not None:
                    raise RuntimeError("Packaged application exited before readiness; inspect local log")
                text = logfile.read_text()
                found = re.search(r"Tomcat started on port (\d+)", text)
                if found:
                    port = int(found.group(1))
                    break
                time.sleep(0.2)
            if port is None:
                raise RuntimeError("Packaged application readiness timeout")
            def check(path, expected):
                request = urllib.request.Request("http://127.0.0.1:" + str(port) + path)
                try:
                    response = urllib.request.urlopen(request, timeout=5)
                except urllib.error.HTTPError as failure:
                    response = failure
                with response:
                    actual = response.status
                    payload = json.loads(response.read())
                    correlation = response.headers.get("X-Request-Id")
                assert actual == expected, (path, actual, payload)
                assert correlation
                if actual >= 400:
                    assert payload["requestId"] == correlation
                checks.append({"profile": profile or "default", "path": path, "expected": expected, "actual": actual, "passed": True})
            check("/health", 200)
            check("/api/foundation/context", 401)
        finally:
            process.terminate()
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=5)

run(None)
result = {"checks": checks, "passed": len(checks), "failed": 0, "boundary": "Local packaged Java process; loopback HTTP; no test accounts or business JDBC or external ERP in this smoke"}
(OUT / "packaged-smoke.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
print(json.dumps({"passed": result["passed"], "failed": result["failed"]}))
