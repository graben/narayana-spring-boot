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

package dev.snowdrop.boot.narayana.core.jdbc;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Properties;

import javax.sql.DataSource;
import javax.sql.XAConnection;
import javax.sql.XADataSource;

import com.arjuna.ats.internal.jdbc.ConnectionImple;
import dev.snowdrop.boot.narayana.core.properties.TransactionalDriverProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link NarayanaDataSource}.
 *
 * @author <a href="mailto:gytis@redhat.com">Gytis Trikleris</a>
 */
@ExtendWith(MockitoExtension.class)
class NarayanaDataSourceTests {

    @Mock
    private XADataSource mockXaDataSource;

    private TransactionalDriverProperties transactionalDriverProperties;
    private NarayanaDataSource dataSourceBean;

    @BeforeEach
    void before() {
        this.transactionalDriverProperties = new TransactionalDriverProperties();
        this.dataSourceBean = new NarayanaDataSource(this.mockXaDataSource, this.transactionalDriverProperties);
    }

    @Test
    void shouldBeAWrapper() {
        assertThat(this.dataSourceBean.isWrapperFor(DataSource.class)).isTrue();
    }

    @Test
    void shouldNotBeAWrapper() {
        assertThat(this.dataSourceBean.isWrapperFor(XADataSource.class)).isFalse();
    }

    @Test
    void shouldUnwrapDataSource() throws SQLException {
        assertThat(this.dataSourceBean.unwrap(DataSource.class)).isInstanceOf(DataSource.class);
        assertThat(this.dataSourceBean.unwrap(DataSource.class)).isSameAs(this.dataSourceBean);
    }

    @Test
    void shouldUnwrapXaDataSource() throws SQLException {
        assertThat(this.dataSourceBean.unwrap(XADataSource.class)).isInstanceOf(XADataSource.class);
        assertThat(this.dataSourceBean.unwrap(XADataSource.class)).isSameAs(this.mockXaDataSource);
    }

    @Test
    void shouldGetSameConnection() throws SQLException {
        this.transactionalDriverProperties.getPool().setEnabled(true);
        Connection connection1 = this.dataSourceBean.getConnection();
        connection1.close();
        Connection connection2 = this.dataSourceBean.getConnection();
        assertThat(connection2).isSameAs(connection1);
    }

    @Test
    void shouldNotGetSameConnection() throws SQLException {
        this.transactionalDriverProperties.getPool().setEnabled(false);
        Connection connection1 = this.dataSourceBean.getConnection();
        connection1.close();
        Connection connection2 = this.dataSourceBean.getConnection();
        assertThat(connection2).isNotSameAs(connection1);
    }

    @Test
    void shouldGetConnectionAndCommit() throws SQLException {
        DatabaseMetaData mockMetaData = mock(DatabaseMetaData.class);
        Connection mockConnection = mock(Connection.class);
        XAConnection mockXaConnection = mock(XAConnection.class);
        given(mockMetaData.getDriverName()).willReturn("mock");
        given(mockMetaData.getDriverMajorVersion()).willReturn(1);
        given(mockMetaData.getDriverMinorVersion()).willReturn(0);
        given(mockConnection.getMetaData()).willReturn(mockMetaData);
        given(mockXaConnection.getConnection()).willReturn(mockConnection);
        given(this.mockXaDataSource.getXAConnection()).willReturn(mockXaConnection);

        Connection connection = this.dataSourceBean.getConnection();
        assertThat(connection).isInstanceOf(ConnectionImple.class);

        connection.commit();

        verify(this.mockXaDataSource, times(1)).getXAConnection();
        verify(mockXaConnection, times(1)).getConnection();
        verify(mockConnection, times(1)).commit();
    }

    private Properties loadAuthProperties() throws IOException {
        Properties properties = new Properties();
        properties.load(NarayanaDataSourceTests.class.getClassLoader().getResourceAsStream("datasourcetests.properties"));
        return properties;
    }

    @Test
    void shouldGetConnectionAndCommitWithCredentials() throws IOException, SQLException {
        Properties authProperties = loadAuthProperties();
        DatabaseMetaData mockMetaData = mock(DatabaseMetaData.class);
        Connection mockConnection = mock(Connection.class);
        XAConnection mockXaConnection = mock(XAConnection.class);
        given(mockMetaData.getDriverName()).willReturn("mock");
        given(mockMetaData.getDriverMajorVersion()).willReturn(1);
        given(mockMetaData.getDriverMinorVersion()).willReturn(0);
        given(mockConnection.getMetaData()).willReturn(mockMetaData);
        given(mockXaConnection.getConnection()).willReturn(mockConnection);
        given(this.mockXaDataSource.getXAConnection(authProperties.getProperty("username"), authProperties.getProperty("username"))).willReturn(mockXaConnection);

        Connection connection = this.dataSourceBean.getConnection(authProperties.getProperty("username"), authProperties.getProperty("username"));
        assertThat(connection).isInstanceOf(ConnectionImple.class);

        connection.commit();

        verify(this.mockXaDataSource, times(1)).getXAConnection(authProperties.getProperty("username"), authProperties.getProperty("username"));
        verify(mockXaConnection, times(1)).getConnection();
        verify(mockConnection, times(1)).commit();
    }
}
