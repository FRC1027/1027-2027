package yams.motorcontrollers.local;

import java.util.List;
import java.util.Optional;
import org.wpilib.math.system.DCMotor;
import org.wpilib.util.Pair;
import yams.motorcontrollers.SmartMotorController;
import yams.motorcontrollers.SmartMotorControllerConfig;
import yams.telemetry.SmartMotorControllerTelemetry.BooleanTelemetryField;
import yams.telemetry.SmartMotorControllerTelemetry.DoubleTelemetryField;

/**
 * NovaWrapper stub for WPILib 2027.
 */
public abstract class NovaWrapper extends SmartMotorController
{
  public NovaWrapper(int canId, DCMotor motor, SmartMotorControllerConfig config)
  {
    m_config = config;
  }

  @Override
  public SmartMotorControllerConfig getConfig()
  {
    return m_config;
  }

  @Override
  public Object getMotorController()
  {
    return null;
  }

  @Override
  public Object getMotorControllerConfig()
  {
    return null;
  }

  @Override
  public Pair<Optional<List<BooleanTelemetryField>>, Optional<List<DoubleTelemetryField>>> getUnsupportedTelemetryFields()
  {
    return Pair.of(Optional.empty(), Optional.empty());
  }
}
