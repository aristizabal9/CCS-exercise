package co.jdao.iac.ccsxercise.collector;

import java.util.List;
import java.util.UUID;

import co.jdao.iac.ccsxercise.base.CcsAbstractStack;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.iot.actions.alpha.FirehosePutRecordAction;
import software.amazon.awscdk.services.iot.actions.alpha.FirehoseRecordSeparator;
import software.amazon.awscdk.services.iot.actions.alpha.KinesisPutRecordAction;
import software.amazon.awscdk.services.iot.alpha.IotSql;
import software.amazon.awscdk.services.iot.alpha.TopicRule;
import software.amazon.awscdk.services.kinesis.Stream;
import software.amazon.awscdk.services.kinesis.StreamMode;
import software.amazon.awscdk.services.kinesisfirehose.alpha.DeliveryStream;
import software.amazon.awscdk.services.kinesisfirehose.destinations.alpha.S3Bucket;
import software.amazon.awscdk.services.s3.Bucket;
import software.constructs.Construct;

public class CcsExerciseCollectorStack extends CcsAbstractStack {

    public CcsExerciseCollectorStack(Construct parent, String name, StackProps props) {
        super(parent, name, props);
        
        Stream stream = Stream.Builder.create(this, "IoTCollectorStream")
            .streamName("IoTCollectorStream")
            .streamMode(StreamMode.ON_DEMAND)
            .retentionPeriod(Duration.hours(24))
            .build();

        Bucket bucket = Bucket.Builder.create(this, "iotcollector-bucket")
            .removalPolicy(RemovalPolicy.DESTROY)
            .autoDeleteObjects(true)
            .enforceSsl(true)
            .versioned(true)
            .build();

        DeliveryStream deliveryStream = DeliveryStream.Builder.create(this, "IoTCollectorDeliveryStream")
            .deliveryStreamName("IoTCollectorDeliveryStream")
            .destinations(List.of(S3Bucket.Builder.create(bucket).build()))
            .sourceStream(stream)
            .build();

        TopicRule.Builder.create(this, "IotKinesisRule")
            .sql(IotSql.fromStringAsVer20160323("SELECT * FROM 'device/data'"))
            .actions(List.of(
                KinesisPutRecordAction.Builder.create(stream)
                    .partitionKey(UUID.randomUUID().toString())
                    .build(),
                FirehosePutRecordAction.Builder.create(deliveryStream)
                    .batchMode(true)
                    .recordSeparator(FirehoseRecordSeparator.NEWLINE)
                    .build()))
            .build();

    }

}
