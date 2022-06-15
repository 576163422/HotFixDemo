package com.faytian.plugin;

import com.android.build.gradle.AppExtension;
import com.android.build.gradle.AppPlugin;
import com.faytian.bean.HotFixExtBean;

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.NonNullApi;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.PluginContainer;

public class PatchPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        //获取所有引入的插件
        PluginContainer plugins = project.getPlugins();
        //引入我们这个插件的前提是，引入application插件，所以需要判断下
        if (!plugins.hasPlugin(AppPlugin.class)) {
            throw new GradleException("必须结合Android Application插件使用该插件");
        }

        // 创建扩展
        project.getExtensions().create("hotfix", HotFixExtBean.class);
//        HotFixExtBean hotfix = project.getExtensions().create("hotfix", HotFixExtBean.class);
//        boolean debugOn = hotfix.isDebugOn();//此时获取不到值，因为apply方法是在引入插件时执行，此时还没有执行到插件拓展（hotfix）里
        project.afterEvaluate(new Action<Project>() {
            @Override
            public void execute(Project project) {
                //build.gradle解析完成后，回调监听
                HotFixExtBean hotfix = project.getExtensions().getByType(HotFixExtBean.class);
                System.out.println(hotfix.getApplicationName());
                //
                AppExtension app = project.getExtensions().getByType(AppExtension.class);
            }
        });

        System.out.println("PatchPlugin------------");
    }
}
