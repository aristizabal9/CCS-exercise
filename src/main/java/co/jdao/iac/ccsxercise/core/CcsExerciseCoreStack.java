package co.jdao.iac.ccsxercise.core;

import java.util.List;

import co.jdao.iac.ccsxercise.base.CcsUtilsResources;
import co.jdao.iac.ccsxercise.base.CcsAbstractStack;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.apigateway.CognitoUserPoolsAuthorizer;
import software.amazon.awscdk.services.events.EventPattern;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.rds.AuroraCapacityUnit;
import software.amazon.awscdk.services.rds.Credentials;
import software.amazon.awscdk.services.rds.DatabaseClusterEngine;
import software.amazon.awscdk.services.rds.DatabaseSecret;
import software.amazon.awscdk.services.rds.ServerlessCluster;
import software.amazon.awscdk.services.rds.ServerlessScalingOptions;
import software.constructs.Construct;

public class CcsExerciseCoreStack extends CcsAbstractStack {

        public CcsExerciseCoreStack(final Construct parent, final String name, final StackProps props) {
                super(parent, name, props);

                Function crud = CcsUtilsResources.createFunction(this, "CrudCoreFunction");

                CognitoUserPoolsAuthorizer authorizer = CcsUtilsResources.createAuthorizer(this, "CrudCore");

                CcsUtilsResources.createApi(this, "CoreApi", authorizer, crud, "customers");

                createRepository();

                CcsUtilsResources.createFunctionWithRule(this, "CustomerAdminRule",
                                EventPattern.builder().source(List.of("crm.customerAdmin"))
                                                .detailType(List.of("create-customer", "update-customer",
                                                                "delete-customer"))
                                                .build(),
                                "CustomerAdminFunction");

                CcsUtilsResources.createFunctionWithRule(this, "EventNotificationRule",
                                EventPattern.builder().source(List.of("collector.alerts"))
                                                .detailType(List.of("unscheduled-stop", "customer-accident",
                                                                "speed-limit-exceeded", "emergency"))
                                                .build(),
                                "EventNotificationFunction");

                CcsUtilsResources.createDistribution(this, "CoreWebApplication");
        }

        private ServerlessCluster createRepository() {
                // Create username and password secret for DB Cluster
                DatabaseSecret secret = DatabaseSecret.Builder.create(this, "CoreSecret")
                                .username("clusteradmin")
                                .build();

                // Create the serverless cluster, provide all values needed to customise the
                // database.
                return ServerlessCluster.Builder.create(this, "CoreDbCluster")
                                .engine(DatabaseClusterEngine.AURORA_MYSQL)
                                .credentials(Credentials.fromSecret(secret))
                                .clusterIdentifier("core-db-cl")
                                .defaultDatabaseName("coredb")
                                .scaling(ServerlessScalingOptions.builder()
                                        .autoPause(Duration.minutes(10))
                                        .minCapacity(AuroraCapacityUnit.ACU_1)
                                        .maxCapacity(AuroraCapacityUnit.ACU_2)
                                        .build())
                                .build();
        }

}
