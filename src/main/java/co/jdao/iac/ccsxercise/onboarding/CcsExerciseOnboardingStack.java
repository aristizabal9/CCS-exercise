package co.jdao.iac.ccsxercise.onboarding;

import java.util.List;

import co.jdao.iac.ccsxercise.base.CcsUtilsResources;
import co.jdao.iac.ccsxercise.base.CcsAbstractStack;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.apigateway.AuthorizationType;
import software.amazon.awscdk.services.apigateway.CognitoUserPoolsAuthorizer;
import software.amazon.awscdk.services.apigateway.IResource;
import software.amazon.awscdk.services.apigateway.Integration;
import software.amazon.awscdk.services.apigateway.LambdaIntegration;
import software.amazon.awscdk.services.apigateway.MethodOptions;
import software.amazon.awscdk.services.apigateway.RestApi;
import software.amazon.awscdk.services.dynamodb.Attribute;
import software.amazon.awscdk.services.dynamodb.AttributeType;
import software.amazon.awscdk.services.dynamodb.BillingMode;
import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.dynamodb.TableProps;
import software.amazon.awscdk.services.events.EventPattern;
import software.amazon.awscdk.services.lambda.Function;
import software.constructs.Construct;

public class CcsExerciseOnboardingStack extends CcsAbstractStack {

    public CcsExerciseOnboardingStack(Construct parent, String name, StackProps props) {
        super(parent, name, props);

        Function crud = CcsUtilsResources.createFunction(this, "CrudOnboardingFunction");

        createRepository(crud);
        
        CognitoUserPoolsAuthorizer authorizer = CcsUtilsResources.createAuthorizer(this, "Onboarding");

        RestApi api = CcsUtilsResources.createApi(this, "OnboardingApi", authorizer, crud, "trucks");

        Function rules = CcsUtilsResources.createFunction(this, "RulesValidationFunction");
        addApiResource(api, authorizer, rules, "rules", List.of("POST"));

        Function finance = CcsUtilsResources.createFunction(this, "FinanceValidationFunction");
        addApiResource(api, authorizer, finance, "finance", List.of("POST"));

        CcsUtilsResources.createFunctionWithRule(this, "IdentityValidationRule",
                EventPattern.builder().source(List.of("identityVerification"))
                        .build(),
                "IdentityValidationFunction");

        CcsUtilsResources.createDistribution(this, "OnboardingWebApplication");
    }

    private void addApiResource(RestApi api, CognitoUserPoolsAuthorizer authorizer, Function fn, String resourceName, List<String> methods) {
        IResource resource = api.getRoot().addResource(resourceName);

        Integration iMethod = new LambdaIntegration(fn);

        MethodOptions methodOptions = MethodOptions.builder()
            .authorizationType(AuthorizationType.COGNITO)
            .authorizer(authorizer)
            .build();
        
        methods.stream().parallel().forEach(method -> resource.addMethod(method, iMethod, methodOptions));
    }

    private void createRepository(Function fn) {
        Attribute partitionKey = Attribute.builder()
                .name("id")
                .type(AttributeType.NUMBER)
                .build();
        TableProps tableProps = TableProps.builder()
                .tableName("OnboardingTable")
                .partitionKey(partitionKey)
                // The default removal policy is RETAIN, which means that cdk destroy will not attempt to delete
                // the new table, and it will remain in your account until manually deleted. By setting the policy to
                // DESTROY, cdk destroy will delete the table (even if it has data in it)
                .removalPolicy(RemovalPolicy.DESTROY)
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .build();
        Table dynamodbTable = new Table(this, "OnboardingTable", tableProps);
        dynamodbTable.grantReadWriteData(fn);
    }

}
