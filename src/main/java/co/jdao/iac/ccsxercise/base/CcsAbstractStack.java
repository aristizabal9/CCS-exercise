package co.jdao.iac.ccsxercise.base;

import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.RemovalPolicyOptions;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.Tags;
import software.constructs.Construct;

public abstract class CcsAbstractStack extends Stack {

    protected CcsAbstractStack(final Construct parent, final String name, final StackProps props) {
        super(parent, name, props);

        Tags tags = Tags.of(parent);
        tags.add("project", (String) this.getNode().tryGetContext("project"));
        tags.add("env", (String) this.getNode().tryGetContext("env"));

        RemovalPolicyOptions.builder()
            .applyToUpdateReplacePolicy(true)
            .defaultValue(RemovalPolicy.DESTROY)
            .build();
    }
}
