#!/usr/bin/env python3
"""Local packaged-JAR smoke. Synthetic credentials remain in memory; no old services are touched."""
import base64
import json
import os
from pathlib import Path
import re
import secrets
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
checks.append({"case": "packaging-excludes-test-entities-and-H2", "passed": True})

def run(profile):
    environment = {key: value for key, value in os.environ.items() if not key.startswith(("SPRING_", "MES_", "SERVER_")) and key not in ("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS")}
    password = secrets.token_urlsafe(24)
    if profile == "demo":
        environment["MES_DEMO_PASSWORD"] = password
    command = [JAVA, "-jar", str(JAR), "--server.port=0", "--server.address=127.0.0.1", "--spring.config.location=classpath:/application.yml", "--spring.profiles.active=" + (profile or "default")]
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
            def check(path, expected, actor=None, factory=None):
                headers = {}
                if actor:
                    headers["Authorization"] = "Basic " + base64.b64encode((actor + ":" + password).encode()).decode()
                if factory:
                    headers["X-Factory-Id"] = factory
                request = urllib.request.Request("http://127.0.0.1:" + str(port) + path, headers=headers)
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
                checks.append({"profile": profile or "default", "path": path, "actor": actor, "factory": factory, "expected": expected, "actual": actual, "passed": True})
            check("/health", 200)
            check("/api/foundation/context", 401)
            if profile == "demo":
                check("/api/foundation/context", 200, "visitor", "A")
                check("/api/foundation/context", 200, "visitor", "B")
                check("/api/foundation/access", 403, "visitor", "A")
                check("/api/foundation/access", 200, "reader", "A")
                check("/api/foundation/access", 403, "reader", "B")
                check("/api/foundation/erp/connection", 422, "reader", "A")
        finally:
            process.terminate()
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=5)

run(None)
run("demo")
result = {"checks": checks, "passed": len(checks), "failed": 0, "boundary": "Local packaged Java process; loopback HTTP; synthetic demo accounts; no business JDBC or external ERP in this smoke"}
(OUT / "packaged-smoke.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
print(json.dumps({"passed": result["passed"], "failed": result["failed"]}))
