package com.lume.build;

import com.android.build.api.instrumentation.*;
import org.objectweb.asm.*;

/** MediaPipe 0.10.32 selects remote stats by default. Use its upstream no-op logger instead. */
public abstract class NoTelemetryFactory
    implements AsmClassVisitorFactory<InstrumentationParameters.None> {
  @Override
  public boolean isInstrumentable(ClassData data) {
    return data.getClassName()
        .equals("com.google.mediapipe.tasks.core.logging.TasksStatsLoggerFactory");
  }

  @Override
  public ClassVisitor createClassVisitor(ClassContext context, ClassVisitor next) {
    return noTelemetryVisitor(next);
  }

  static ClassVisitor noTelemetryVisitor(ClassVisitor next) {
    return new ClassVisitor(Opcodes.ASM9, next) {
      int replaced;

      @Override
      public MethodVisitor visitMethod(
          int access, String name, String descriptor, String signature, String[] exceptions) {
        return new MethodVisitor(
            Opcodes.ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {
          @Override
          public void visitMethodInsn(
              int opcode, String owner, String method, String desc, boolean itf) {
            if (owner.equals("com/google/mediapipe/tasks/core/logging/TasksStatsProtoLogger")
                && method.equals("create")
                && opcode == Opcodes.INVOKESTATIC) {
              replaced++;
              super.visitMethodInsn(
                  opcode,
                  "com/google/mediapipe/tasks/core/logging/TasksStatsDummyLogger",
                  method,
                  desc.replace("TasksStatsProtoLogger;", "TasksStatsDummyLogger;"),
                  itf);
            } else super.visitMethodInsn(opcode, owner, method, desc, itf);
          }
        };
      }

      @Override
      public void visitEnd() {
        if (replaced != 1)
          throw new IllegalStateException(
              "MediaPipe stats factory changed: review privacy patch before updating dependency");
        super.visitEnd();
      }
    };
  }
}
