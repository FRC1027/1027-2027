// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.subsystems.swervedrive;

import static org.wpilib.units.Units.Meter;
import static org.wpilib.units.Units.MetersPerSecond;

import com.pathplanner.lib.auto.AutoBuilder;
import com.pathplanner.lib.commands.PathPlannerAuto;
import com.pathplanner.lib.commands.PathfindingCommand;
import com.pathplanner.lib.config.PIDConstants;
import com.pathplanner.lib.config.RobotConfig;
import com.pathplanner.lib.controllers.PPHolonomicDriveController;
import com.pathplanner.lib.path.PathConstraints;
import com.pathplanner.lib.path.PathPlannerPath;
import com.pathplanner.lib.util.DriveFeedforwards;
import com.pathplanner.lib.util.swerve.SwerveSetpoint;
import com.pathplanner.lib.util.swerve.SwerveSetpointGenerator;

import org.wpilib.math.linalg.VecBuilder;
import org.wpilib.math.geometry.Pose2d;
import org.wpilib.math.geometry.Rotation2d;
import org.wpilib.math.geometry.Translation2d;
import org.wpilib.math.kinematics.ChassisVelocities;
import org.wpilib.math.kinematics.SwerveDriveKinematics;
import org.wpilib.math.trajectory.Trajectory;
import org.wpilib.math.util.Units;
import org.wpilib.networktables.DoubleArrayPublisher;
import org.wpilib.networktables.DoubleArraySubscriber;
import org.wpilib.networktables.NetworkTable;
import org.wpilib.networktables.NetworkTableInstance;
import org.wpilib.driverstation.MatchState;
import org.wpilib.driverstation.Alliance;
import org.wpilib.driverstation.DriverStationErrors;
import org.wpilib.system.Timer;
import org.wpilib.command2.Command;
import org.wpilib.command2.CommandScheduler;
import org.wpilib.command2.Commands;
import org.wpilib.command2.SubsystemBase;

import frc.robot.util.Constants.RobotProperties;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

import org.json.simple.parser.ParseException;

import yams.mechanisms.swerve.SwerveDrive;
import yams.mechanisms.config.SwerveDriveConfig;
import swervelib.parser.SwerveParser;
import swervelib.telemetry.SwerveDriveTelemetry;
import swervelib.telemetry.SwerveDriveTelemetry.TelemetryVerbosity;
import com.ctre.phoenix6.hardware.Pigeon2;
import com.ctre.phoenix6.configs.Pigeon2Configuration;

public class SwerveSubsystem extends SubsystemBase
{
  private final SwerveDrive swerveDrive;
  private final boolean visionDriveTest = false;

  // NT4 Topics for Limelight MegaTag2 updates
  private final NetworkTable limelightTable;
  private final DoubleArraySubscriber botPoseSub;
  private final DoubleArrayPublisher robotOrientationPub;

  /**
   * Initialize SwerveDrive with the JSON directory provided.
   */
  public SwerveSubsystem(File directory)
  {
    SwerveDriveTelemetry.verbosity = TelemetryVerbosity.HIGH;

    this.limelightTable = NetworkTableInstance.getDefault().getTable("limelight");
    this.botPoseSub = limelightTable.getDoubleArrayTopic("botpose_orb_wpiblue").subscribe(new double[0]);
    this.robotOrientationPub = limelightTable.getDoubleArrayTopic("robot_orientation_set").publish();

    try
    {
      SwerveDriveConfig driveConfig = new SwerveDriveConfig();

      new SwerveParser(directory);
      swerveDrive = SwerveParser.createSwerveDrive(driveConfig);
      configurePigeon2();
    } catch (Exception e)
    {
      throw new RuntimeException(e);
    }

    if (visionDriveTest)
    {
      swerveDrive.stopOdometryThread();
    }
    setupPathPlanner();
  }

  public void updateVisionOdometry() {
    double yawDeg = swerveDrive.getYaw().getDegrees();
    robotOrientationPub.set(new double[]{yawDeg, 0, 0, 0, 0, 0});

    double[] botPose = botPoseSub.get();
    if (botPose != null && botPose.length >= 8) {
      double tagCount = botPose[7];
      if (tagCount > 0) {
        Pose2d pose = new Pose2d(botPose[0], botPose[1], Rotation2d.fromDegrees(botPose[5]));
        double latencySeconds = botPose[6] / 1000.0;
        double timestamp = Timer.getTimestamp() - latencySeconds;
        swerveDrive.addVisionMeasurement(pose, timestamp, VecBuilder.fill(0.7, 0.7, 9999999));
      }
    }
  }

  private void configurePigeon2()
  {
    try {
      Object imu = swerveDrive.getGyro();
      if (imu instanceof Pigeon2) 
      {
        try (Pigeon2 pigeon = (Pigeon2) imu) {
          Pigeon2Configuration config = new Pigeon2Configuration();
          pigeon.getConfigurator().refresh(config);

          config.MountPose.MountPosePitch = 0;
          config.MountPose.MountPoseRoll = 0;
          config.MountPose.MountPoseYaw = 0;

          pigeon.getConfigurator().apply(config);
        }
      }
    } catch (Exception e) {
      DriverStationErrors.reportWarning("Could not configure Pigeon 2 mount pose: " + e.getMessage(), false);
    }
  }

  @Override
  public void periodic()
  {
    if (visionDriveTest)
    {
      swerveDrive.updateOdometry();
    }
    updateVisionOdometry();
  }

  public void setupPathPlanner()
  {
    RobotConfig config;
    try
    {
      config = RobotConfig.fromGUISettings();
      final boolean enableFeedforward = true;

      AutoBuilder.configure(
          this::getPose,
          this::resetOdometry,
          this::getRobotVelocity,
          (speedsRobotRelative, moduleFeedForwards) -> {
            if (enableFeedforward)
            {
              swerveDrive.drive(
                  speedsRobotRelative,
                  swerveDrive.kinematics.toSwerveModuleVelocities(speedsRobotRelative),
                  moduleFeedForwards.linearForces()
              );
            } else
            {
              swerveDrive.drive(speedsRobotRelative);
            }
          },
          new PPHolonomicDriveController(
              new PIDConstants(5.0, 0.0, 0.0),
              new PIDConstants(5.0, 0.0, 0.0)
          ),
          config,
          () -> {
            var alliance = MatchState.getAlliance();
            return alliance.isPresent() && alliance.get() == Alliance.RED;
          },
          this
      );
    } catch (Exception e)
    {
      e.printStackTrace();
    }

    CommandScheduler.getInstance().schedule(PathfindingCommand.warmupCommand());
  }

  public Command getAutonomousCommand(String pathName)
  {
    return new PathPlannerAuto(pathName);
  }

  public Command driveToPose(Pose2d pose)
  {
    PathConstraints constraints = new PathConstraints(
        RobotProperties.MAX_SPEED, 4.0,
        Units.degreesToRadians(540), Units.degreesToRadians(720));

    return AutoBuilder.pathfindToPose(
        pose,
        constraints,
        MetersPerSecond.of(0)
    );
  }

  public Command centerModulesCommand()
  {
    return run(() -> Arrays.asList(swerveDrive.getModules()).forEach(it -> it.setAngle(0.0)));
  }

  public Command driveToDistanceCommand(double distanceInMeters, double speedInMetersPerSecond)
  {
    return run(() -> drive(new ChassisVelocities(speedInMetersPerSecond, 0, 0)))
        .until(() -> swerveDrive.getPose().getTranslation().getDistance(new Translation2d(0, 0)) > distanceInMeters);
  }

  public Command driveCommand(DoubleSupplier translationX, DoubleSupplier translationY, DoubleSupplier angularRotationX)
  {
    return run(() -> {
      double xMps = translationX.getAsDouble() * RobotProperties.MAX_SPEED * 0.8;
      double yMps = translationY.getAsDouble() * RobotProperties.MAX_SPEED * 0.8;
      double omegaRad = Math.pow(angularRotationX.getAsDouble(), 3) * Units.degreesToRadians(360);

      swerveDrive.drive(new Translation2d(xMps, yMps), omegaRad, true, false);
    });
  }

  public void drive(Translation2d translation, double rotation, boolean fieldRelative)
  {
    swerveDrive.drive(translation, rotation, fieldRelative, false);
  }

  public void driveFieldOriented(ChassisVelocities velocity)
  {
    swerveDrive.driveFieldOriented(velocity);
  }

  public Command driveFieldOriented(Supplier<ChassisVelocities> velocity)
  {
    return run(() -> swerveDrive.driveFieldOriented(velocity.get()));
  }

  public void drive(ChassisVelocities velocity)
  {
    swerveDrive.drive(velocity);
  }

  public SwerveDriveKinematics getKinematics()
  {
    return swerveDrive.kinematics;
  }

  public void resetOdometry(Pose2d initialHolonomicPose)
  {
    swerveDrive.resetOdometry(initialHolonomicPose);
  }

  public Pose2d getPose()
  {
    return swerveDrive.getPose();
  }

  public void zeroGyro()
  {
    swerveDrive.zeroGyro();
  }

  private boolean isRedAlliance()
  {
    var alliance = MatchState.getAlliance();
    return alliance.isPresent() && alliance.get() == Alliance.RED;
  }

  public void zeroGyroWithAlliance()
  {
    if (isRedAlliance())
    {
      zeroGyro();
      resetOdometry(new Pose2d(getPose().getTranslation(), Rotation2d.fromDegrees(180)));
    } else
    {
      zeroGyro();
    }
  }

  public void setMotorBrake(boolean brake)
  {
    swerveDrive.setMotorIdleMode(brake);
  }

  public Rotation2d getHeading()
  {
    return getPose().getRotation();
  }

  public ChassisVelocities getFieldVelocity()
  {
    return swerveDrive.getFieldVelocity();
  }

  public ChassisVelocities getRobotVelocity()
  {
    return swerveDrive.getRobotVelocity();
  }

  public void lock()
  {
    swerveDrive.lockPose();
  }

  public Rotation2d getPitch()
  {
    return swerveDrive.getPitch();
  }

  public void addFakeVisionReading()
  {
    swerveDrive.addVisionMeasurement(new Pose2d(3, 3, Rotation2d.fromDegrees(65)), Timer.getTimestamp());
  }

  public SwerveDrive getSwerveDrive()
  {
    return swerveDrive;
  }
}