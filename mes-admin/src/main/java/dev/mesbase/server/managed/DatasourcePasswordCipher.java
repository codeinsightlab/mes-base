package dev.mesbase.server.managed;

import java.util.Arrays;
import java.util.Base64;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;



/** 数据库只保存认证密文；密钥由运行环境提供。 */
public class DatasourcePasswordCipher {
    private final byte[] key;
    public DatasourcePasswordCipher(String encodedKey) {
        key=Base64.getDecoder().decode(encodedKey);
        if(key.length!=32) throw new IllegalArgumentException("数据源密钥必须是 32 字节的 Base64 编码");
    }

    public static void main(String[] args) {
//        String s = Base64.getEncoder().encodeToString("V51iLPsROJrHUeEUfjdjPIZYldVyj56hb1wL3YNRGS0=".getBytes());
        DatasourcePasswordCipher datasourcePasswordCipher = new DatasourcePasswordCipher("V51iLPsROJrHUeEUfjdjPIZYldVyj56hb1wL3YNRGS0=");
        System.out.println(datasourcePasswordCipher.decrypt("6977a79016ce7f497094296d322b27e014e694bae32113b8Aa1!"));
//        System.out.println(Arrays.toString(datasourcePasswordCipher.key));
    }
    public String encrypt(String plaintext) {
        try {
            byte[] iv=new byte[12];new SecureRandom().nextBytes(iv);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,iv));
            byte[] encrypted=cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] result=new byte[iv.length+encrypted.length];
            System.arraycopy(iv,0,result,0,iv.length);System.arraycopy(encrypted,0,result,iv.length,encrypted.length);
            return Base64.getEncoder().encodeToString(result);
        } catch(java.security.GeneralSecurityException e) {throw new IllegalStateException("数据源密码加密失败",e);}
    }
    public String decrypt(String encoded) {
        try {
            byte[] value=Base64.getDecoder().decode(encoded);
            if(value.length<28) throw new IllegalArgumentException("Invalid encrypted credential");
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,value,0,12));
            return new String(cipher.doFinal(value,12,value.length-12),java.nio.charset.StandardCharsets.UTF_8);
        } catch(java.security.GeneralSecurityException | IllegalArgumentException e) {throw new IllegalStateException("数据源密码无法解密，请检查运行环境密钥",e);}
    }
}
