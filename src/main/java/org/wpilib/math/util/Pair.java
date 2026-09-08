package org.wpilib.math.util;

/**
 * 2027 compatibility bridge for org.wpilib.math.util.Pair delegating to
 * org.wpilib.util.Pair.
 *
 * @param <A> the first type
 * @param <B> the second type
 */
public class Pair<A, B> extends org.wpilib.util.Pair<A, B> {

  public Pair(A first, B second) {
    super(first, second);
  }

  public static <A, B> Pair<A, B> of(A a, B b) {
    return new Pair<>(a, b);
  }
}
