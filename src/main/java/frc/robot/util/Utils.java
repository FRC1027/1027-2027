package frc.robot.util;

import com.limelightvision.Limelight;
import com.limelightvision.Limelight.LimelightResults;
import com.limelightvision.Limelight.FiducialTarget;
import org.wpilib.math.util.Units;
import org.wpilib.system.Timer;
import frc.robot.util.Constants.ObjectRecognitionConstants;
import frc.robot.util.Constants.RobotProperties;

/**
 * Shared utility methods used across robot subsystems and commands.
 */
public final class Utils {

  // Stores the last valid distance calculated by the Limelight.
  private static double lastValidDistance = 0.0;
  // Stores the last time the AprilTag was seen.
  private static double lastSeenTime = 0.0;

  private Utils() {} // Prevent instantiation

  /**
   * Applies a deadband and rescales the remaining joystick range back to full scale.
   *
   * @param JoystickValue joystick input value (expected range: [-1.0, 1.0])
   * @param DeadbandCutOff deadband threshold (expected range: [0.0, 1.0])
   * @return 0.0 when inside the deadband, otherwise a rescaled value in [-1.0, 1.0]
   */
  public static double deadbandReturn(double JoystickValue, double DeadbandCutOff) {
    double deadbandReturn;
    if (JoystickValue < DeadbandCutOff && JoystickValue > (DeadbandCutOff * (-1))) {
      // Inside the deadband window: output zero.
      deadbandReturn = 0;
    } else {
      // Outside the deadband: remove offset while preserving sign, then rescale to full range.
      deadbandReturn = (JoystickValue - (Math.abs(JoystickValue) / JoystickValue * DeadbandCutOff)) / (1 - DeadbandCutOff);
    }
    return deadbandReturn;
  }

  /**
   * Calculates the Euclidean distance from the bumper to the target tag using Limelight data.
   *
   * @param limelight a Limelight object reference that can be used to retrieve pose information.
   * @return Distance from bumper to target tag in meters, or NaN if the Limelight pose is unavailable.
   */
  public static double calculateDistanceToTarget(Limelight limelight) {
    // 1. Fetch the unified local results frame context object
    LimelightResults results = limelight.getLatestResults();
    
    // 2. Validate targeting conditions using 2027 built-in metrics
    boolean hasTarget = limelight.hasTarget();
    
    // Get array of all visible fiducials from this specific snapshot frame
    FiducialTarget[] fiducials = results.fiducialTargets;
    
    // Default our tracked fiducial properties if none are matched 
    double fid = 0;
    double[] pose = new double[0];

    // Grab the primary/closest target tracking data if any targets exist
    if (fiducials != null && fiducials.length > 0) {
      fid = fiducials[0].fiducialId;
      pose = fiducials[0].targetPoseCameraSpace; // X, Y, Z coordinates relative to camera frame
    }

    // Gets the current time in seconds.
    double currentTime = Timer.getTimestamp();
    System.out.println("Pose Length: " + pose.length + " Fiducial ID: " + fid);

    if (pose.length >= 3 && (fid == 4 || fid == 10 || fid == 26)) {
      double tx = pose[0]; // Horizontal offset (left/right) in meters.
      double ty = pose[1]; // Vertical offset (up/down) in meters.
      double tz = pose[2]; // Forward distance (depth) in meters.

      // Assume the camera is pitched up by the mount angle. We rotate the Z distance down to horizontal.
      double mountAngle = ObjectRecognitionConstants.LIMELIGHT_MOUNT_ANGLE_RADIANS;
      double tzPlanar = tz * Math.cos(mountAngle) + ty * Math.sin(mountAngle);
      System.out.println("tz: " + Units.metersToInches(tz) + " ty: " + ty + " tzPlanar: " + Units.metersToInches(tzPlanar));

      // Compute horizontal planar distance from camera to tag using X/Z components.
      double cameraToTag = Math.sqrt(tx * tx + tzPlanar * tzPlanar);

      // Convert from camera distance to shooter distance
      double shooterToTag = Math.max(0.0, (cameraToTag - RobotProperties.CAM_TO_SHOOTER_DISTANCE));

      // Print statement for debugging: should print true repeatedly if Limelight flicker mitigation works correctly.
      System.out.println("TV: " + hasTarget + " Distance: " + shooterToTag);

      // Sets the last valid shooter to tag distance that was calculated to the last valid distance.
      lastValidDistance = shooterToTag;
      // Sets the current time to the last time an AprilTag was seen.
      lastSeenTime = currentTime;

      // Return the distance from the shooter to the AprilTag.
      return shooterToTag;
    } else {
      // Checks to see if we "just" lost the target, ignoring small Limelight detection fluctuations.
      if (currentTime - lastSeenTime < ObjectRecognitionConstants.LIMELIGHT_TARGET_TIMEOUT) {
        return lastValidDistance;
      }
    }

    // If no AprilTag is detected, return 0 and exit the method.
    return 0.0;
  }
}
