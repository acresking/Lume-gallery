package il.luma.gallery;

import java.util.*;

/** Bounded snapshots; callers supply independent copies. */
final class EditorHistory<T> {
  private final ArrayDeque<T> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
  private final int limit;

  EditorHistory(int limit) {
    this.limit = limit;
  }

  void record(T previous) {
    undo.addLast(previous);
    while (undo.size() > limit) undo.removeFirst();
    redo.clear();
  }

  boolean canUndo() {
    return !undo.isEmpty();
  }

  boolean canRedo() {
    return !redo.isEmpty();
  }

  T undo(T current) {
    if (undo.isEmpty()) return current;
    redo.addLast(current);
    return undo.removeLast();
  }

  T redo(T current) {
    if (redo.isEmpty()) return current;
    undo.addLast(current);
    return redo.removeLast();
  }

  void clear() {
    undo.clear();
    redo.clear();
  }
}
