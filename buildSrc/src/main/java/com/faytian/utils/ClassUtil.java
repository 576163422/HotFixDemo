package com.faytian.utils;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.InputStream;

public class ClassUtil {
    public static byte[] referHackWhenInit(InputStream inputStream) throws IOException {
        // class的解析器
        ClassReader cr = new ClassReader(inputStream);
        // class的输出器
        ClassWriter cw = new ClassWriter(cr, 0);
        // class访问者，相当于回调，解析器解析的结果，回调给访问者
        ClassVisitor cv = new ClassVisitor(Opcodes.ASM5, cw) {

            //要在构造方法里插桩 init
            @Override
            public MethodVisitor visitMethod(int access, final String name, String desc, String signature, String[] exceptions) {
                //监听方法解析
                MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
                mv = new MethodVisitor(Opcodes.ASM5, mv) {
                    @Override
                    public void visitInsn(int opcode) {
                        //在构造方法中插入AntilazyLoad引用
                        if ("<init>".equals(name) && opcode == Opcodes.RETURN) {
                            //引用类型
                            //基本数据类型 : I J Z
                            super.visitLdcInsn(Type.getType("Lcom/example/hack/HackLoad;"));
                        }
                        super.visitInsn(opcode);
                    }
                };
                return mv;
            }
        };
        //启动分析
        cr.accept(cv, 0);
        return cw.toByteArray();
    }
}
