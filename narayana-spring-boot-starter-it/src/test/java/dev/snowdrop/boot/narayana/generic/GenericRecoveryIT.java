/*
 * Copyright 2020 Red Hat, Inc, and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.snowdrop.boot.narayana.generic;

import java.time.Duration;
import java.util.LinkedList;
import java.util.List;

import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import jakarta.transaction.TransactionManager;

import com.arjuna.ats.internal.jta.recovery.arjunacore.XARecoveryModule;
import com.arjuna.ats.jta.recovery.XAResourceRecoveryHelper;
import dev.snowdrop.boot.narayana.app.EntriesService;
import dev.snowdrop.boot.narayana.app.Entry;
import dev.snowdrop.boot.narayana.app.MessagesService;
import dev.snowdrop.boot.narayana.app.TestApplication;
import dev.snowdrop.boot.narayana.core.properties.NarayanaProperties;
import dev.snowdrop.boot.narayana.utils.BytemanHelper;
import io.agroal.springframework.boot.AgroalDataSourceAutoConfiguration;
import org.jboss.byteman.contrib.bmunit.BMRule;
import org.jboss.byteman.contrib.bmunit.BMUnitConfig;
import org.jboss.byteman.contrib.bmunit.WithByteman;
import org.jboss.tm.XAResourceWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.doAnswer;
import static org.mockito.BDDMockito.when;

/**
 * @author <a href="mailto:gytis@redhat.com">Gytis Trikleris</a>
 */
@WithByteman
@BMUnitConfig
@SpringBootTest(classes = TestApplication.class)
@EnableAutoConfiguration(exclude = AgroalDataSourceAutoConfiguration.class)
public class GenericRecoveryIT {
    // Margin in seconds to compensate for a complete run through recovery code within waits
    private static final int RECOVERY_CODE_EXECUTION_MARGIN = 1;

    @Mock
    private XAResourceWrapper xaResource;

    @Mock
    private XAResourceRecoveryHelper xaResourceRecoveryHelper;

    @Autowired
    private MessagesService messagesService;

    @Autowired
    private EntriesService entriesService;

    @Autowired
    private TransactionManager transactionManager;

    @Autowired
    private XARecoveryModule xaRecoveryModule;

    @Autowired
    private NarayanaProperties narayanaProperties;

    @BeforeEach
    void before() {
        MockitoAnnotations.openMocks(this);
        this.messagesService.clearReceivedMessages();
        this.entriesService.clearEntries();
        BytemanHelper.reset();
    }

    @Test
    @BMRule(name = "Fail before commit",
            targetClass = "com.arjuna.ats.arjuna.coordinator.BasicAction",
            targetMethod = "phase2Commit",
            targetLocation = "ENTRY",
            helper = "dev.snowdrop.boot.narayana.utils.BytemanHelper",
            action = "incrementCommitsCounter(); failFirstCommit($0.get_uid());")
    void testCrashBeforeCommit() throws Exception {
        // Setup dummy XAResource and its recovery helper
        setupXaMocks();

        this.transactionManager.begin();
        this.transactionManager.getTransaction()
                .enlistResource(this.xaResource);
        this.messagesService.sendMessage("test-message");
        Entry entry = this.entriesService.createEntry("test-value");
        try {
            // Byteman rule will cause commit to fail
            this.transactionManager.commit();
            fail("Exception was expected");
        } catch (Exception ignored) {
        }

        // Just after crash message and entry shouldn't be available
        assertMessagesAfterCrash(this.messagesService.getReceivedMessages());
        assertEntriesAfterCrash(this.entriesService.getEntries());

        // Maximum time to wait for a recovery run. Keeping in mind the worst case scenario:
        // With a crash after first scan, but before backoffPeriod, the total time will be
        // BackoffPeriod + PeriodicRecoveryPeriod + BackoffPeriod and some extra time for
        // code execution other than waiting.
        int recoveryWaitTime = this.narayanaProperties.getRecoveryBackoffPeriod() +
                        this.narayanaProperties.getPeriodicRecoveryPeriod() +
                        this.narayanaProperties.getRecoveryBackoffPeriod() +
                        RECOVERY_CODE_EXECUTION_MARGIN;

        await("Wait for the recovery to happen")
                .atMost(Duration.ofSeconds(recoveryWaitTime))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> {
                    assertMessagesAfterRecovery(this.messagesService.getReceivedMessages());
                    assertEntriesAfterRecovery(this.entriesService.getEntries());
                });
    }

    protected void assertEntriesAfterCrash(List<Entry> entries) {
        assertThat(entries)
                .as("No entry should exist")
                .isEmpty();
    }

    protected void assertMessagesAfterCrash(List<String> messages) {
        assertThat(messages)
                .as("No message should exist")
                .isEmpty();
    }

    protected void assertEntriesAfterRecovery(List<Entry> entries) {
        assertThat(entries)
                .as("Test entry should exist after transaction was committed")
                .hasSize(1);
    }

    protected void assertMessagesAfterRecovery(List<String> messages) {
        assertThat(messages)
                .as("Test message should have been received after transaction was committed")
                .hasSize(1);
    }

    private void setupXaMocks() throws Exception {
        // XAResourceWrapper metadata values
        when(this.xaResource.getJndiName()).thenReturn("mock");
        when(this.xaResource.getProductName()).thenReturn("Narayana Fake Integration");
        when(this.xaResource.getProductVersion()).thenReturn("1.0.0");
        List<Xid> xids = new LinkedList<>();
        // Save Xid provided during prepare
        when(this.xaResource.prepare(any(Xid.class)))
                .then(i -> {
                    xids.add((Xid) i.getArguments()[0]);
                    return XAResource.XA_OK;
                });
        // Return Xids when recovering
        when(this.xaResource.recover(anyInt()))
                .then(i -> xids.toArray(new Xid[xids.size()]));
        // Remove Xid provided during commit
        doAnswer(i -> xids.remove((Xid) i.getArguments()[0]))
                .when(this.xaResource).commit(any(Xid.class), anyBoolean());
        // Return XAResource when recovering
        when(this.xaResourceRecoveryHelper.getXAResources())
                .thenReturn(new XAResource[]{ this.xaResource });
        this.xaRecoveryModule.addXAResourceRecoveryHelper(this.xaResourceRecoveryHelper);
    }
}
