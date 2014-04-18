package au.com.dius.pact.consumer;

import au.com.dius.pact.model.Interaction;
import au.com.dius.pact.model.Pact;
import org.junit.Test;
import scalaz.Validation;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public abstract class AbstractConsumerPactTest {
    protected abstract Interaction createInteraction(ConsumerInteractionJavaDsl builder);
    protected abstract String providerName();
    protected abstract String consumerName();

    private Pact createPact() {
        return ConsumerPactJavaDsl.makePact()
                .withConsumer(consumerName())
                .withProvider(providerName())
                .withInteractions(createInteraction(new ConsumerInteractionJavaDsl()));
    }

    protected abstract void runTest(String endpoint);

    @Test
    public void testPact() {
        Pact pact = createPact();

        int port = (int)PactServerConfig.randomPort().get();
        final PactServerConfig config = new PactServerConfig(port, "localhost");

        String state = pact.interactions().head().providerState();

        Validation result = new ConsumerPact(pact).runConsumer(config, state,
            new Runnable() {
                public void run() {
                    try {
                        runTest(config.url());
                    } catch(Exception e) {
                        fail("error thrown"+e);
                    }
                }
            });
        assertTrue(result.toString(), result.isSuccess());
    }
}
