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

package dev.snowdrop.boot.narayana.core.properties;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import com.arjuna.ats.arjuna.common.CoordinatorEnvironmentBean;
import com.arjuna.ats.arjuna.common.CoreEnvironmentBean;
import com.arjuna.ats.arjuna.common.CoreEnvironmentBeanException;
import com.arjuna.ats.arjuna.common.ObjectStoreEnvironmentBean;
import com.arjuna.ats.arjuna.common.RecoveryEnvironmentBean;
import com.arjuna.ats.jdbc.common.JDBCEnvironmentBean;
import com.arjuna.ats.jta.common.JTAEnvironmentBean;
import com.arjuna.common.internal.util.propertyservice.BeanPopulator;
import org.springframework.beans.factory.InitializingBean;

/**
 * Bean that configures Narayana transaction manager.
 *
 * @author <a href="mailto:gytis@redhat.com">Gytis Trikleris</a>
 */
public class NarayanaPropertiesInitializer implements InitializingBean {

    private static final String HASH_ALGORITHM_FOR_SHORTENING = "SHA-224";

    private final NarayanaProperties properties;

    public NarayanaPropertiesInitializer(NarayanaProperties narayanaProperties) {
        this.properties = narayanaProperties;
    }

    @Override
    public void afterPropertiesSet() {
        setNodeIdentifier(this.properties.getNodeIdentifier(), this.properties.isShortenNodeIdentifierIfNecessary());
        setXARecoveryNodes(this.properties.getXaRecoveryNodes());
        setObjectStoreDir(this.properties.getLogDir());
        setCommitOnePhase(this.properties.isOnePhaseCommit());
        setDefaultTimeout(this.properties.getDefaultTimeout());
        setPeriodicRecoveryPeriod(this.properties.getPeriodicRecoveryPeriod());
        setRecoveryBackoffPeriod(this.properties.getRecoveryBackoffPeriod());
        setExpiryScanInterval(this.properties.getExpiryScanInterval());
        setXaResourceOrphanFilters(this.properties.getXaResourceOrphanFilters());
        setXAResourceRecordWrappingPlugin(this.properties.getXaResourceRecordWrappingPlugin());
        setLastResourceOptimisationInterface(this.properties.getLastResourceOptimisationInterface());
        setCommitMarkableResourceJNDINames(this.properties.getCommitMarkableResourceJNDINames());
        setRecoveryModules(this.properties.getRecoveryModules());
        setExpiryScanners(this.properties.getExpiryScanners());
        setDefaultIsolationLevel(this.properties.getTransactionalDriver().getDefaultIsolationLevel().getLevel());
        setDefaultIsSameRMOverride(this.properties.getTransactionalDriver().isDefaultIsSameRMOverride());
    }

    private void setNodeIdentifier(String nodeIdentifier, boolean shortenNodeIdentifierIfNecessary) {
        try {
            if (nodeIdentifier != null
                    && nodeIdentifier.getBytes(StandardCharsets.UTF_8).length > 28
                    && shortenNodeIdentifierIfNecessary) {
                getPopulator(CoreEnvironmentBean.class).setNodeIdentifier(shortenNodeIdentifier(nodeIdentifier));
            } else {
                getPopulator(CoreEnvironmentBean.class).setNodeIdentifier(nodeIdentifier);
            }
        } catch (CoreEnvironmentBeanException | NoSuchAlgorithmException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private String shortenNodeIdentifier(String nodeIdentifier) throws NoSuchAlgorithmException {
        byte[] nodeIdentifierAsBytes = nodeIdentifier.getBytes(StandardCharsets.UTF_8);
        MessageDigest messageDigest224 = MessageDigest.getInstance(HASH_ALGORITHM_FOR_SHORTENING);
        byte[] hashedByteArray = messageDigest224.digest(nodeIdentifierAsBytes);
        byte[] base64Result = Base64.getEncoder().encode(hashedByteArray);
        byte[] slice = Arrays.copyOfRange(base64Result, 0, 28);
        return new String(slice, StandardCharsets.UTF_8);
    }

    private void setXARecoveryNodes(List<String> xaRecoveryNodes) {
        if (xaRecoveryNodes.isEmpty()) {
            xaRecoveryNodes = List.of(getPopulator(CoreEnvironmentBean.class).getNodeIdentifier());
        }
        getPopulator(JTAEnvironmentBean.class).setXaRecoveryNodes(xaRecoveryNodes);
    }

    private void setObjectStoreDir(String objectStoreDir) {
        if (objectStoreDir != null) {
            getPopulator(ObjectStoreEnvironmentBean.class).setObjectStoreDir(objectStoreDir);
            getPopulator(ObjectStoreEnvironmentBean.class, "communicationStore").setObjectStoreDir(objectStoreDir);
            getPopulator(ObjectStoreEnvironmentBean.class, "stateStore").setObjectStoreDir(objectStoreDir);
        }
    }

    private void setCommitOnePhase(boolean isCommitOnePhase) {
        getPopulator(CoordinatorEnvironmentBean.class).setCommitOnePhase(isCommitOnePhase);
    }

    private void setDefaultTimeout(int defaultTimeout) {
        getPopulator(CoordinatorEnvironmentBean.class).setDefaultTimeout(defaultTimeout);
    }

    private void setPeriodicRecoveryPeriod(int periodicRecoveryPeriod) {
        getPopulator(RecoveryEnvironmentBean.class).setPeriodicRecoveryPeriod(periodicRecoveryPeriod);
    }

    private void setRecoveryBackoffPeriod(int recoveryBackoffPeriod) {
        getPopulator(RecoveryEnvironmentBean.class).setRecoveryBackoffPeriod(recoveryBackoffPeriod);
    }

    private void setExpiryScanInterval(int expiryScanInterval) {
        getPopulator(RecoveryEnvironmentBean.class).setExpiryScanInterval(expiryScanInterval);
    }

    private void setXaResourceOrphanFilters(List<String> xaResourceOrphanFilters) {
        getPopulator(JTAEnvironmentBean.class).setXaResourceOrphanFilterClassNames(xaResourceOrphanFilters);
    }

    private void setXAResourceRecordWrappingPlugin(String xaResourceRecordWrappingPlugin) {
        getPopulator(JTAEnvironmentBean.class).setXaResourceRecordWrappingPluginClassName(xaResourceRecordWrappingPlugin);
    }

    private void setLastResourceOptimisationInterface(String lastResourceOptimisationInterface) {
        getPopulator(JTAEnvironmentBean.class).setLastResourceOptimisationInterfaceClassName(lastResourceOptimisationInterface);
    }

    private void setCommitMarkableResourceJNDINames(List<String> commitMarkableResourceJNDINames) {
        getPopulator(JTAEnvironmentBean.class).setCommitMarkableResourceJNDINames(commitMarkableResourceJNDINames);
    }

    private void setRecoveryModules(List<String> recoveryModules) {
        getPopulator(RecoveryEnvironmentBean.class).setRecoveryModuleClassNames(recoveryModules);
    }

    private void setExpiryScanners(List<String> expiryScanners) {
        getPopulator(RecoveryEnvironmentBean.class).setExpiryScannerClassNames(expiryScanners);
    }

    private void setDefaultIsolationLevel(int defaultIsolationLevel) {
        getPopulator(JDBCEnvironmentBean.class).setIsolationLevel(defaultIsolationLevel);
    }

    private void setDefaultIsSameRMOverride(boolean defaultIsSameRMOverride) {
        getPopulator(JDBCEnvironmentBean.class).setDefaultIsSameRMOverride(defaultIsSameRMOverride);
    }

    private <T> T getPopulator(Class<T> beanClass) {
        return BeanPopulator.getDefaultInstance(beanClass);
    }

    private <T> T getPopulator(Class<T> beanClass, String name) {
        return BeanPopulator.getNamedInstance(beanClass, name);
    }

}
