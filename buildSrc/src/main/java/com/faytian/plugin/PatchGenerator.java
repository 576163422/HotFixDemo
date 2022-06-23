package com.faytian.plugin;

import com.android.build.gradle.AppExtension;
import com.faytian.utils.PatchUtil;

import org.apache.tools.ant.taskdefs.condition.Os;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.process.ExecSpec;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

public class PatchGenerator {
    private String buildToolsVersion;

    private Map<String, String> oldMd5Map;
    private JarOutputStream jos;

    private Project project;
    private File patchClassFile;
    private File patchDexFile;


    public PatchGenerator(Project project, File md5File, File patchClassFile, File patchDexFile) {
        this.project = project;
        this.patchClassFile = patchClassFile;
        this.patchDexFile = patchDexFile;

        AppExtension android = project.getExtensions().getByType(AppExtension.class);
        buildToolsVersion = android.getBuildToolsVersion();
        if (md5File.exists()) {
            oldMd5Map = PatchUtil.readHex(md5File);
        }
    }

    /**
     * 与上次编译的class md5进行对比， 不一致代表类有新增或变化，需要进行热修复， 写入patchClassFile中
     * @param className
     * @param md5
     * @param byteCode
     */
    public void checkClass(String className, String md5, byte[] byteCode) {
        if (oldMd5Map == null || oldMd5Map.isEmpty()) {
            return;
        }
        String oldClassMD5 = oldMd5Map.get(className);
        //md5为null说明是新增加的类，不一样代表有变动
        if (oldClassMD5 == null || !oldClassMD5.equals(md5)) {
            JarOutputStream output = getOutput();
            try {
                //通过className 创建JarEntry，进行写入操作
                output.putNextEntry(new JarEntry(className));
                output.write(byteCode);
                output.closeEntry();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 执行dx命令， 构建补丁包
     * @throws Exception
     */
    public void generate() throws Exception {
        if (!patchClassFile.exists()) {
            return;
        }
        JarOutputStream output = getOutput();
        output.close();
        //获得sdk目录，因为dx命令在 sdk中
        Properties properties = new Properties();
        File localProps = project.getRootProject().file("local.properties");
        String sdkDir;
        if (localProps.exists()) {
            properties.load(new FileInputStream(localProps));
            sdkDir = properties.getProperty("sdk.dir");
        } else {
            sdkDir = System.getenv("ANDROID_HOME");
        }
        //windows使用 dx.bat命令,linux/mac使用 dx命令
        String cmdExt = Os.isFamily(Os.FAMILY_WINDOWS) ? ".bat" : "";
        // 执行：dx --dex --output=output.jar input.jar
        final String dxPath = sdkDir + "/build-tools/" + buildToolsVersion + "/dx" + cmdExt;
        final String patch = "--output=" + patchDexFile.getAbsolutePath();
        project.exec(new Action<ExecSpec>() {
            @Override
            public void execute(ExecSpec execSpec) {
                execSpec.commandLine(dxPath, "--dex", patch, patchClassFile.getAbsolutePath());
            }
        });
        //删除用于生成补丁包的jar
        patchClassFile.delete();
        project.getLogger().error("\npatch generated in : " + patchDexFile);//输出log
    }


    /**
     * 通过补丁包file, 得到JarOutputStream，进行写入操作
     * @return
     */
    private JarOutputStream getOutput() {
        if (jos == null) {
            try {
                jos = new JarOutputStream(new FileOutputStream(patchClassFile));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return jos;
    }
}