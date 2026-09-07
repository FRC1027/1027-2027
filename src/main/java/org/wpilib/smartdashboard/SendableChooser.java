package org.wpilib.smartdashboard;

import java.util.function.Consumer;
import org.wpilib.tunable.ComplexTunable;
import org.wpilib.tunable.Selectable;
import org.wpilib.tunable.TunableTable;

/**
 * 2027 compatibility bridge for {@link SendableChooser} backed by {@link Selectable}.
 *
 * @param <V> The type of the values to be stored
 */
public class SendableChooser<V> implements ComplexTunable, AutoCloseable {
  private final Selectable<V> m_selectable = new Selectable<>();

  public SendableChooser() {}

  /**
   * Adds the given object to the list of options.
   *
   * @param name the name of the option
   * @param object the option
   */
  public void addOption(String name, V object) {
    m_selectable.add(name, object);
  }

  /**
   * Adds the given object to the list of options and marks it as the default.
   *
   * @param name the name of the option
   * @param object the option
   */
  public void setDefaultOption(String name, V object) {
    m_selectable.addDefault(name, object);
  }

  /**
   * Returns the selected option.
   *
   * @return the option selected
   */
  public V getSelected() {
    return m_selectable.getSelected();
  }

  /**
   * Bind a listener to change events on the selected option.
   *
   * @param listener the listener to call on change
   */
  public void onChange(Consumer<V> listener) {
    m_selectable.onChange(listener);
  }

  @Override
  public void publishTunable(TunableTable table) {
    m_selectable.publishTunable(table);
  }

  @Override
  public String getTunableType() {
    return m_selectable.getTunableType();
  }

  @Override
  public void close() {
    m_selectable.clear();
  }
}
