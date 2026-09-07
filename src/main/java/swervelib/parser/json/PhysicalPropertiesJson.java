package swervelib.parser.json;

import io.avaje.jsonb.Json;
import swervelib.parser.json.modules.GearingJson;

/**
 * Used to configure the SwerveModule.
 */
@Json
public class PhysicalPropertiesJson
{
  /**
   * Conversion Factors composition. Auto-calculates the conversion factors.
   */
  public GearingJson    gearing            = new GearingJson();
  /**
   * The current limit in AMPs to apply to the motors.
   */
  public MotorConfigInt statorCurrentLimit = new MotorConfigInt(40, 20);

}

