package com.lume.build;

import static org.junit.Assert.*;

import org.junit.Test;
import org.objectweb.asm.*;

public class NoTelemetryFactoryTest {
  @Test
  public void redirectsRemoteLogger() {
    ClassWriter original = new ClassWriter(0);
    original.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "Factory", null, "java/lang/Object", null);
    MethodVisitor m =
        original.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "create", "()Ljava/lang/Object;", null, null);
    m.visitCode();
    for (int i = 0; i < 3; i++) m.visitInsn(Opcodes.ACONST_NULL);
    m.visitMethodInsn(
        Opcodes.INVOKESTATIC,
        "com/google/mediapipe/tasks/core/logging/TasksStatsProtoLogger",
        "create",
        "(Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;)Lcom/google/mediapipe/tasks/core/logging/TasksStatsProtoLogger;",
        false);
    m.visitInsn(Opcodes.ARETURN);
    m.visitMaxs(3, 0);
    m.visitEnd();
    original.visitEnd();
    ClassWriter output = new ClassWriter(0);
    new ClassReader(original.toByteArray())
        .accept(NoTelemetryFactory.noTelemetryVisitor(output), 0);
    int[] calls = {0};
    new ClassReader(output.toByteArray())
        .accept(
            new ClassVisitor(Opcodes.ASM9) {
              @Override
              public MethodVisitor visitMethod(int a, String n, String d, String s, String[] e) {
                return new MethodVisitor(Opcodes.ASM9) {
                  @Override
                  public void visitMethodInsn(
                      int o, String owner, String name, String desc, boolean itf) {
                    assertFalse(owner.contains("TasksStatsProtoLogger"));
                    if (owner.endsWith("TasksStatsDummyLogger")) {
                      assertTrue(desc.endsWith("TasksStatsDummyLogger;"));
                      calls[0]++;
                    }
                  }
                };
              }
            },
            0);
    assertEquals(1, calls[0]);
  }

  @Test
  public void changedFactoryFailsClosed() {
    var v = NoTelemetryFactory.noTelemetryVisitor(new ClassWriter(0));
    assertThrows(IllegalStateException.class, v::visitEnd);
  }
}
