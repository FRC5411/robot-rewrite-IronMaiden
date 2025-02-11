
package frc.robot.subsystems.swerve;

import edu.wpi.first.math.controller.ProfiledPIDController;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.kinematics.SwerveModulePosition;
import edu.wpi.first.math.kinematics.SwerveModuleState;
import edu.wpi.first.math.trajectory.TrapezoidProfile;
import edu.wpi.first.wpilibj.DriverStation;
import frc.lib.util.swerveUtil.CTREModuleState;
import frc.lib.util.swerveUtil.SwerveModuleConstants;

// import com.ctre.phoenix.sensors.CANCoder;
import com.ctre.phoenix6.hardware.CANcoder;
import com.revrobotics.CANSparkMax;
import com.revrobotics.RelativeEncoder;
import com.revrobotics.SparkPIDController;
import com.revrobotics.CANSparkBase.ControlType;
import com.revrobotics.CANSparkBase.FaultID;
import com.revrobotics.CANSparkLowLevel.MotorType;

/**
 * a Swerve Modules using REV Robotics motor controllers and CTRE CANcoder absolute encoders.
 */
public class SwerveMod implements ISwerveModule
{
    public int moduleNumber;
    private Rotation2d angleOffset;

    private CANSparkMax mAngleMotor;
    private CANSparkMax mDriveMotor;

    private CANcoder angleEncoder;
    private RelativeEncoder relAngleEncoder;
    private RelativeEncoder relDriveEncoder;


    public SwerveMod(int moduleNumber, SwerveModuleConstants moduleConstants)
    {
        this.moduleNumber = moduleNumber;
        this.angleOffset = moduleConstants.angleOffset;
        
       
        /* Angle Motor Config */
        mAngleMotor = new CANSparkMax(moduleConstants.angleMotorID, MotorType.kBrushless);
        configAngleMotor();

        /* Drive Motor Config */
        mDriveMotor = new CANSparkMax(moduleConstants.driveMotorID,  MotorType.kBrushless);
        configDriveMotor();

         /* Angle Encoder Config */
        angleEncoder = new CANcoder(moduleConstants.cancoderID);
        configEncoders();


       // lastAngle = getState().angle;
    }


    private void configEncoders()
    {     
        // absolute encoder   
      
        // angleEncoder.configFactoryDefault();
        // angleEncoder.configAllSettings(new SwerveConfig().canCoderConfig);
       
        angleEncoder.getConfigurator().apply(new SwerveConfig().canCoderConfig);
        relDriveEncoder = mDriveMotor.getEncoder();
        relDriveEncoder.setPosition(0);

         
        relDriveEncoder.setPositionConversionFactor(SwerveConfig.driveRevToMeters);
        relDriveEncoder.setVelocityConversionFactor(SwerveConfig.driveRpmToMetersPerSecond);

        
        relAngleEncoder = mAngleMotor.getEncoder();
        relAngleEncoder.setPositionConversionFactor(SwerveConfig.DegreesPerTurnRotation);
        // in degrees/sec
        relAngleEncoder.setVelocityConversionFactor(SwerveConfig.DegreesPerTurnRotation / 60);
    

        resetToAbsolute();
        // mDriveMotor.burnFlash();
        // mAngleMotor.burnFlash();
        
    }

    private void configAngleMotor()
    {
        mAngleMotor.restoreFactoryDefaults();
        SparkPIDController controller = mAngleMotor.getPIDController();
            //new TrapezoidProfile.Constraints(3, 3));
        controller.setP(SwerveConfig.angleKP, 0);
        controller.setI(SwerveConfig.angleKI,0);
        controller.setD(SwerveConfig.angleKD,0);
        controller.setFF(SwerveConfig.angleKF,0);
        controller.setOutputRange(-SwerveConfig.anglePower, SwerveConfig.anglePower);
        mAngleMotor.setSmartCurrentLimit(SwerveConfig.angleContinuousCurrentLimit);
        mAngleMotor.setSecondaryCurrentLimit(60);
       
        mAngleMotor.setInverted(SwerveConfig.angleMotorInvert);
        mAngleMotor.setIdleMode(SwerveConfig.angleIdleMode);

        mAngleMotor.burnFlash();
       
    }

    private void configDriveMotor()
    {        
        mDriveMotor.restoreFactoryDefaults();
        // ProfiledPIDController controller = new ProfiledPIDController(0.0, SwerveConfig.driveKI, SwerveConfig.driveKD, 
        // new TrapezoidProfile.Constraints(1, 1));
        SparkPIDController controller = mDriveMotor.getPIDController();
        controller.setP(SwerveConfig.driveKP,0);
        controller.setI(SwerveConfig.driveKI,0);
        controller.setD(SwerveConfig.driveKD,0);

        controller.setFF(SwerveConfig.driveKF,0);
        controller.setOutputRange(-SwerveConfig.drivePower, SwerveConfig.drivePower);
        mDriveMotor.setSmartCurrentLimit(SwerveConfig.driveContinuousCurrentLimit);
        mDriveMotor.setSecondaryCurrentLimit(60);
        mDriveMotor.setInverted(SwerveConfig.driveMotorInvert);
        mDriveMotor.setIdleMode(SwerveConfig.driveIdleMode); 

        mDriveMotor.burnFlash();
    }



    public void setDesiredState(SwerveModuleState desiredState, boolean isOpenLoop)
    {
        
        
        // CTREModuleState functions for any motor type.
        desiredState = CTREModuleState.optimize(desiredState, getState().angle); 
        setAngle(desiredState);
        setSpeed(desiredState, isOpenLoop);

        if(mDriveMotor.getFault(FaultID.kSensorFault))
        {
            DriverStation.reportWarning("Sensor Fault on Drive Motor ID:"+mDriveMotor.getDeviceId(), false);
        }

        if(mAngleMotor.getFault(FaultID.kSensorFault))
        {
            DriverStation.reportWarning("Sensor Fault on Angle Motor ID:"+mAngleMotor.getDeviceId(), false);
        }
    }

    private void setSpeed(SwerveModuleState desiredState, boolean isOpenLoop)
    {
       
        if(isOpenLoop)
        {
            double percentOutput = desiredState.speedMetersPerSecond / SwerveConfig.maxSpeed;
            mDriveMotor.set(percentOutput);
            return;
        }
 
        double velocity = desiredState.speedMetersPerSecond;
        
        SparkPIDController controller = mDriveMotor.getPIDController();
        controller.setReference(velocity, ControlType.kVelocity, 0);
        
    }

    private void setAngle(SwerveModuleState desiredState)
    {
        if(Math.abs(desiredState.speedMetersPerSecond) <= (SwerveConfig.maxSpeed * 0.01)) 
        {
            mAngleMotor.stopMotor();
            return;

        }
        Rotation2d angle = desiredState.angle; 
        //Prevent rotating module if speed is less then 1%. Prevents Jittering.
        
        SparkPIDController controller = mAngleMotor.getPIDController();
        
        double degReference = angle.getDegrees();
     
       
        
        controller.setReference (degReference, ControlType.kPosition, 0);
        
    }

   
    public CANSparkMax getDriveMotor() {
        return mDriveMotor;
    }

    public CANSparkMax getAngleMotor() {
        return mAngleMotor;
    }

    private Rotation2d getAngle()
    {
        return Rotation2d.fromDegrees(relAngleEncoder.getPosition());
    }

    public Rotation2d getCanCoder()
    {
        
        return Rotation2d.fromRotations(angleEncoder.getAbsolutePosition().getValueAsDouble());
        //return getAngle();
    }

    public int getModuleNumber() 
    {
        return moduleNumber;
    }

    public void setModuleNumber(int moduleNumber) 
    {
        this.moduleNumber = moduleNumber;
    }

    private void resetToAbsolute()
    {
    
        double absolutePosition =getCanCoder().getDegrees() - angleOffset.getDegrees();
        relAngleEncoder.setPosition(absolutePosition);
    }

  

    public SwerveModuleState getState()
    {
        return new SwerveModuleState(
            relDriveEncoder.getVelocity(),
            getAngle()
        ); 
    }

    public SwerveModulePosition getPosition()
    {
        return new SwerveModulePosition(
            relDriveEncoder.getPosition(), 
            getAngle()
        );
    }
}