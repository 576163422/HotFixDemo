import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class PluginDemo {
    public static void test() {
        InputStream is = null;
        ByteArrayOutputStream bos = null;
        try {
            //java代码执行命令行
            Process exec = Runtime.getRuntime().exec("java -version");
            exec.waitFor();
            //获取输出
            is = exec.getErrorStream();
            bos = new ByteArrayOutputStream();
            int len;
            byte[] buffer = new byte[4096];
            while ((len = is.read(buffer)) != -1) {
                bos.write(buffer, 0, len);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (bos != null) {
                System.out.println("Java执行命令行，打印输出 >>> " + bos.toString());
                try {
                    bos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}