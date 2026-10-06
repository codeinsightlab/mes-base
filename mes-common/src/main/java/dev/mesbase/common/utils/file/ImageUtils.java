package dev.mesbase.common.utils.file;
/** Source Excel image adapter: arbitrary URL/local path reads are refused. */
public final class ImageUtils {
 public static byte[] getImage(String imagePath){throw new dev.mesbase.common.exception.ServiceException("图片需通过私有文件接口读取");}
}
