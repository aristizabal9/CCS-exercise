package co.jdao.iac.ccsxercise;

import co.jdao.iac.ccsxercise.admin.CcsExerciseAdminStack;
import co.jdao.iac.ccsxercise.collector.CcsExerciseCollectorStack;
import co.jdao.iac.ccsxercise.core.CcsExerciseCoreStack;
import co.jdao.iac.ccsxercise.onboarding.CcsExerciseOnboardingStack;
import software.amazon.awscdk.App;

public class CcsExerciseApp {
    
    public static void main(String[] args) {
        App app = new App();
        new CcsExerciseCoreStack(app, "CcsExerciseCoreStack", null);
        new CcsExerciseAdminStack(app, "CcsExerciseAdminStack", null);
        new CcsExerciseOnboardingStack(app, "CcsExerciseOnboardingStack", null);
        new CcsExerciseCollectorStack(app, "CcsExerciseCollectorStack", null);

        app.synth();
    }
}
