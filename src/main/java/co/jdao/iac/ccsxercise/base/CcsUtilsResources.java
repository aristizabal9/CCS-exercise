package co.jdao.iac.ccsxercise.base;

import java.util.List;
import java.util.Map;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.services.apigateway.AuthorizationType;
import software.amazon.awscdk.services.apigateway.CognitoUserPoolsAuthorizer;
import software.amazon.awscdk.services.apigateway.IResource;
import software.amazon.awscdk.services.apigateway.Integration;
import software.amazon.awscdk.services.apigateway.LambdaIntegration;
import software.amazon.awscdk.services.apigateway.MethodOptions;
import software.amazon.awscdk.services.apigateway.RestApi;
import software.amazon.awscdk.services.apigateway.RestApiProps;
import software.amazon.awscdk.services.cloudfront.AllowedMethods;
import software.amazon.awscdk.services.cloudfront.BehaviorOptions;
import software.amazon.awscdk.services.cloudfront.CachePolicy;
import software.amazon.awscdk.services.cloudfront.Distribution;
import software.amazon.awscdk.services.cloudfront.OriginAccessIdentity;
import software.amazon.awscdk.services.cloudfront.OriginRequestPolicy;
import software.amazon.awscdk.services.cloudfront.ResponseHeadersPolicy;
import software.amazon.awscdk.services.cloudfront.ViewerProtocolPolicy;
import software.amazon.awscdk.services.cloudfront.origins.S3Origin;
import software.amazon.awscdk.services.cognito.AuthFlow;
import software.amazon.awscdk.services.cognito.Mfa;
import software.amazon.awscdk.services.cognito.StandardAttribute;
import software.amazon.awscdk.services.cognito.StandardAttributes;
import software.amazon.awscdk.services.cognito.UserPool;
import software.amazon.awscdk.services.cognito.UserPoolClient;
import software.amazon.awscdk.services.events.EventPattern;
import software.amazon.awscdk.services.events.Rule;
import software.amazon.awscdk.services.events.targets.LambdaFunction;
import software.amazon.awscdk.services.lambda.Architecture;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.FunctionProps;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.CorsRule;
import software.amazon.awscdk.services.s3.HttpMethods;
import software.amazon.awscdk.services.sqs.Queue;
import software.constructs.Construct;

public class CcsUtilsResources {

    private CcsUtilsResources() {
        throw new IllegalStateException("Utility class");
    }

    public static Function createFunction(Construct parent, String lambdaId) {
        return new Function(parent, lambdaId,
                getLambdaFunctionProps(null, 300, 512).build());
    }

    public static FunctionProps.Builder getLambdaFunctionProps(Map<String, String> lambdaEnvMap, int duration,
            int memorySize) {
        return FunctionProps.builder()
                .runtime(Runtime.NODEJS_LATEST)
                .handler("index.handler")
                .code(Code.fromInline("exports.handler = handler.toString()"))
                .architecture(Architecture.ARM_64)
                .environment(lambdaEnvMap)
                .timeout((duration == 0 || duration > 900) ? Duration.seconds(15) : Duration.seconds(duration))
                .memorySize(memorySize < 512 ? 512 : memorySize);
    }

    public static RestApi createApi(Construct parent, String apiName, CognitoUserPoolsAuthorizer authorizer, Function crud, String resourceName) {
        RestApi api = new RestApi(parent, apiName, RestApiProps.builder().restApiName(apiName).build());

        IResource resource = api.getRoot().addResource(resourceName);

        MethodOptions methodOptions = MethodOptions.builder()
            .authorizationType(AuthorizationType.COGNITO)
            .authorizer(authorizer)
            .build();

        Integration getAllMethod = new LambdaIntegration(crud);
        resource.addMethod("GET", getAllMethod, methodOptions);

        Integration postMethod = new LambdaIntegration(crud);
        resource.addMethod("POST", postMethod, methodOptions);

        IResource customerResource = resource.addResource("{id}");

        Integration getMethod = new LambdaIntegration(crud);
        customerResource.addMethod("GET", getMethod, methodOptions);

        Integration deleteMethod = new LambdaIntegration(crud);
        customerResource.addMethod("DELETE", deleteMethod, methodOptions);

        return api;
    }

    public static Function createFunctionWithRule(Construct parent, String ruleName, EventPattern ruleDef, String lambdaId) {
        Rule rule = Rule.Builder.create(parent, ruleName)
                .eventPattern(ruleDef)
                .build();

        Queue queue = new Queue(parent, ruleName + "DLQ");

        Function function = new Function(parent, lambdaId,
                CcsUtilsResources.getLambdaFunctionProps(null, 300, 512).build());

        rule.addTarget(LambdaFunction.Builder.create(function)
                .deadLetterQueue(queue) // Optional: add a dead letter queue
                .maxEventAge(Duration.hours(2)) // Optional: set the maxEventAge retry policy
                .retryAttempts(2)
                .build());

        return function;
    }

    public static CognitoUserPoolsAuthorizer createAuthorizer(Construct parent, String name) {
        UserPool userPool = UserPool.Builder.create(parent, name + "UserPool")
            .userPoolName(name + "UserPool")
            .selfSignUpEnabled(true)
            .signInCaseSensitive(false)
            .mfa(Mfa.OFF)
            .standardAttributes(StandardAttributes.builder()
                .fullname(StandardAttribute.builder().required(true).mutable(true).build())
                .email(StandardAttribute.builder().required(true).mutable(true).build())
                .build())
            .removalPolicy(RemovalPolicy.DESTROY)
            .build();

        UserPoolClient.Builder.create(parent, name + "Client")
            .userPool(userPool)
            .authFlows(AuthFlow.builder()
                .userPassword(true)
                .build())
            .refreshTokenValidity(Duration.hours(1))
            .build();

        return CognitoUserPoolsAuthorizer.Builder.create(parent, name + "Authorizer")
            .cognitoUserPools(List.of(userPool))
            .build();
    }

    public static Distribution createDistribution(Construct parent, String name) {
        Bucket hostingBucket = Bucket.Builder.create(parent, name.toLowerCase() + "-bucket")
            .removalPolicy(RemovalPolicy.DESTROY)
            .autoDeleteObjects(true)
            .enforceSsl(true)
            .versioned(true)
            .build();

        hostingBucket.addCorsRule(CorsRule.builder()
            .allowedMethods(List.of(HttpMethods.GET, HttpMethods.POST, HttpMethods.DELETE))
            .allowedHeaders(List.of("*"))
            .allowedOrigins(List.of("*"))
            .exposedHeaders(List.of("Access-Control-Allow-Origin"))
            .build());

        OriginAccessIdentity oai = OriginAccessIdentity.Builder.create(parent, name + "-oai").build();

        hostingBucket.grantRead(oai);

        return Distribution.Builder.create(parent, name)
            .defaultRootObject("index.html")
            .defaultBehavior(BehaviorOptions.builder()
                .origin(S3Origin.Builder.create(hostingBucket)
                    .originAccessIdentity(oai)
                    .build())
                .originRequestPolicy(OriginRequestPolicy.CORS_S3_ORIGIN)
                .viewerProtocolPolicy(ViewerProtocolPolicy.REDIRECT_TO_HTTPS)
                .responseHeadersPolicy(ResponseHeadersPolicy.SECURITY_HEADERS)
                .cachePolicy(CachePolicy.CACHING_OPTIMIZED)
                .allowedMethods(AllowedMethods.ALLOW_ALL)
                .build())
            .build();
    }

}
