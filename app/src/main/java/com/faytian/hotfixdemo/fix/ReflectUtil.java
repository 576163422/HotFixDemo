package com.faytian.hotfixdemo.fix;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class ReflectUtil {


    /**
     * 从 instance 到其父类 找 name 属性
     *
     * @param instance
     * @param name
     * @return
     * @throws NoSuchFieldException
     */
    public static Field findField(Object instance, String name) throws NoSuchFieldException {
        Class<?> clazz = instance.getClass();
        while (clazz != null) {
            try {
                //查找当前类的 属性(不包括父类)
                Field field = clazz.getDeclaredField(name);
                if (!field.isAccessible()) {
                    field.setAccessible(true);
                }
                return field;
            } catch (NoSuchFieldException e) {
                // 当前类没有找到，继续到父类找
            }
            clazz = clazz.getSuperclass();
        }
        //一直都没有找到抛出异常
        throw new NoSuchFieldException("Field " + name + " not found in " + instance.getClass());
    }

    /**
     * 从 instance 到其父类 找  name 方法
     *
     * @param instance
     * @param name
     * @return
     * @throws NoSuchFieldException
     */
    public static Method findMethod(Object instance, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        Class<?> clazz = instance.getClass();
        while (clazz != null) {
            try {
                //查找当前类的 方法(不包括父类)
                Method method = clazz.getDeclaredMethod(name, parameterTypes);
                if (!method.isAccessible()) {
                    method.setAccessible(true);
                }
                return method;
            } catch (NoSuchMethodException e) {
                // 当前类没有找到，继续到父类找
            }
            clazz = clazz.getSuperclass();
        }
        //一直都没有找到抛出异常
        throw new NoSuchMethodException("Method " + name + " not found in " + instance.getClass());
    }
}
