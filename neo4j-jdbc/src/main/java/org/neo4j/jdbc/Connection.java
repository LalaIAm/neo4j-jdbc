/**
 * Copyright (c) 2016 LARUS Business Automation [http://www.larus-ba.it]
 * <p>
 * This file is part of the "LARUS Integration Framework for Neo4j".
 * <p>
 * The "LARUS Integration Framework for Neo4j" is licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p>
 * Created on 03/02/16
 */
package org.neo4j.jdbc;

import org.neo4j.jdbc.utils.ExceptionBuilder;

import java.sql.*;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

/**
 * @author AgileLARUS
 * @since 3.0.0
 */
public abstract class Connection implements java.sql.Connection {

	/**
	 * Is the connection is in readonly mode ?
	 */
	private boolean readOnly = false;

	/**
	 * JDBC driver properties.
	 */
	protected Properties properties;

	protected int         holdability;

	/**
	 * Default constructor with properties.
	 *
	 * @param properties Driver properties
	 * @param defaultHoldability
	 */
	protected Connection(Properties properties, int defaultHoldability) {
		this.properties = properties;
		this.holdability = defaultHoldability;
	}

	public static boolean hasDebug(Properties properties) {
		return "true".equalsIgnoreCase(properties.getProperty("debug", "false"));
	}

	/*---------------------------------------*/
	/*       Some useful check method        */
	/*---------------------------------------*/

	/**
	 * Check if this connection is closed or not.
	 * If it's closed, then we throw a SQLException, otherwise we do nothing.
	 *
	 * @throws SQLException
	 */
	protected void checkClosed() throws SQLException {
		if (this.isClosed()) {
			throw new SQLException("Connection already closed");
		}
	}

	/**
	 * Method to check if we are into autocommit mode.
	 * If we do, then it throw an exception.
	 * This method is for using into commit and rollback method.
	 *
	 * @throws SQLException
	 */
	protected void checkAutoCommit() throws SQLException {
		if (this.getAutoCommit()) {
			throw new SQLException("Cannot commit when in autocommit");
		}
	}

	/**
	 * Check if can execute the query into the current mode (ie. readonly or not).
	 * If we can't an SQLException is throw.
	 *
	 * @param query Cypher query
	 * @throws SQLException
	 */
	protected void checkReadOnly(String query) throws SQLException {
		if (isReadOnly() && isMutating(query)) {
			throw new SQLException("Mutating Query in readonly mode: " + query);
		}
	}

	/**
	 * Detect some cypher keyword to know if this query mutated the graph.
	 * /!\ This not enough now due to procedure procedure.
	 *
	 * @param query Cypher query
	 * @return
	 */
	private boolean isMutating(String query) {
		return query.matches("(?is).*\\b(create|relate|delete|set)\\b.*");
	}

	/**
	 * Check if the holdability parameter is conform to specification.
	 * If it doesn't, we throw an exception.
	 *
	 * @param resultSetHoldability The holdability value to check
	 * @throws SQLException
	 * {@link java.sql.Connection#setHoldability(int)}
	 */
	protected void checkHoldabilityParams(int resultSetHoldability) throws SQLException {
		// @formatter:off
		if( resultSetHoldability != ResultSet.HOLD_CURSORS_OVER_COMMIT &&
			resultSetHoldability != ResultSet.CLOSE_CURSORS_AT_COMMIT
		){
			throw new SQLFeatureNotSupportedException();
		}
		// @formatter:on
	}

	/**
	 * Check if the concurrency parameter is conform to specification.
	 * If it doesn't, we throw an exception.
	 *
	 * @param resultSetConcurrency The concurrency value to check
	 * @throws SQLException
	 */
	protected void checkConcurrencyParams(int resultSetConcurrency) throws SQLException {
		// @formatter:off
		if( resultSetConcurrency != ResultSet.CONCUR_UPDATABLE &&
			resultSetConcurrency != ResultSet.CONCUR_READ_ONLY
		){
			throw new SQLFeatureNotSupportedException();
		}
		// @formatter:on
	}

	/**
	 * Check if the resultset type parameter is conform to specification.
	 * If it doesn't, we throw an exception.
	 *
	 * @param resultSetType The concurrency value to check
	 * @throws SQLException
	 */
	protected void checkTypeParams(int resultSetType) throws SQLException {
		// @formatter:off
		if( resultSetType != ResultSet.TYPE_FORWARD_ONLY &&
			resultSetType != ResultSet.TYPE_SCROLL_INSENSITIVE &&
			resultSetType != ResultSet.TYPE_SCROLL_SENSITIVE
		){
			throw new SQLFeatureNotSupportedException();
		}
		// @formatter:on
	}

	/*------------------------------------*/
	/*       Default implementation       */
	/*------------------------------------*/

	@Override public void setReadOnly(boolean readOnly) throws SQLException {
		this.checkClosed();
		this.readOnly = readOnly;
	}

	@Override public boolean isReadOnly() throws SQLException {
		this.checkClosed();
		return this.readOnly;
	}

	@Override public void setHoldability(int holdability) throws SQLException {
		this.checkClosed();
		this.checkHoldabilityParams(holdability);
		this.holdability = holdability;
	}

	@Override public int getHoldability() throws SQLException {
		this.checkClosed();
		return this.holdability;
	}

	/**
	 * Default implementation of setCatalog.
	 * Neo4j doesn't implement catalog feature, so we do nothing to avoid some tools exception.
	 */
	@Override public void setCatalog(String catalog) throws SQLException {
		this.checkClosed();
		return;
	}

	/**
	 * Default implementation of getCatalog.
	 * Neo4j doesn't implement catalog feature, so return <code>null</code> (@see {@link java.sql.Connection#getCatalog})
	 */
	@Override public String getCatalog() throws SQLException {
		this.checkClosed();
		return null;
	}

	/**
	 * Default implementation of getTransactionIsolation.
	 */
	@Override public int getTransactionIsolation() throws SQLException {
		this.checkClosed();
		return TRANSACTION_READ_COMMITTED;
	}

	/**
	 * Default implementation of nativeSQL.
	 * Here we should implement some hacks for JDBC tools if needed.
	 * This method must be used before running a query.
	 */
	@Override public String nativeSQL(String sql) throws SQLException {
		return sql;
	}

	@Override public <T> T unwrap(Class<T> iface) throws SQLException {
		return org.neo4j.jdbc.Wrapper.unwrap(iface, this);
	}

	@Override public boolean isWrapperFor(Class<?> iface) throws SQLException {
		return org.neo4j.jdbc.Wrapper.isWrapperFor(iface, this.getClass());
	}

	/*-----------------------------*/
	/*       Abstract method       */
	/*-----------------------------*/

	@Override public abstract org.neo4j.jdbc.DatabaseMetaData getMetaData() throws SQLException;

	@Override public abstract void setAutoCommit(boolean autoCommit) throws SQLException;

	@Override public abstract boolean getAutoCommit() throws SQLException;

	@Override abstract public void commit() throws SQLException;

	@Override abstract public void rollback() throws SQLException;

	@Override public abstract Statement createStatement() throws SQLException;

	@Override public abstract Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException;

	@Override public abstract Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException;

	@Override public abstract PreparedStatement prepareStatement(String sql) throws SQLException;

	@Override public abstract PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException;

	@Override public abstract PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability)
			throws SQLException;

	@Override public abstract void close() throws SQLException;

	@Override public abstract boolean isClosed() throws SQLException;

	/*---------------------------------*/
	/*       Not implemented yet       */
	/*---------------------------------*/

	@Override public java.sql.CallableStatement prepareCall(String sql) throws SQLException {
		throw ExceptionBuilder.buildUnsupportedOperationException();
	}

	@Override public void setTransactionIsolation(int level) throws SQLException {
		throw ExceptionBuilder.buildUnsupportedOperationException();
	}

	@Override public SQLWarning getWarnings() throws SQLException {
		throw ExceptionBuilder.buildUnsupportedOperationException();
	}

	@Override public void clearWarnings() throws SQLException {
		throw ExceptionBuilder.buildUnsupportedOperationException();
	}

	@Override public java.sql.CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
		throw ExceptionBuilder.buildUnsupportedOperationException();
	}

	@Override public Map<String, Class<?>> getTypeMap() throws SQLException {
		throw ExceptionBuilder.buildUnsupportedOperationException();
	}

	@Override public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
		throw ExceptionBuilder.buildUnsupportedOperationException();
	}

	@Override public Savepoint setSavepoint() throws SQLException {
		throw ExceptionBuilder.buildUnsupportedOperationException();
	}

	@Override public Savepoint setSavepoint(String name) throws SQLException {
		throw ExceptionBuilder.buildUnsupportedOperationException();
	}

	@Override public void rollback(Savepoint savepoint) throws SQLException {
		throw ExceptionBuilder.buildUnsupportedOperationException();
	}

	@Override public void releaseSavepoint(Savepoint savepoint) throws SQLException {
		throw ExceptionBuilder.buildUnsupportedOperationException();
	}

	@Override public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
		throw ExceptionBuilder.buildUnsupportedOperationException();
	}

	@Override public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
		throw ExceptionBuilder.buildUnsupportedOperationException();
	}

	@Override public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
		throw ExceptionBuilder.buildUnsupportedOperationException();
	}

	@Override public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
		throw ExceptionBuilder.buildUnsupportedOperationException();
	}

	@Override public Clob createClob() throws SQLException {
		throw ExceptionBuilder.buildUnsupportedOperationException();
	}

	@Override public Blob createBlob() throws SQLException {
		throw ExceptionBuilder.buildUnsupportedOperationException();
	}

	@Override public NClob createNClob() throws SQLException {
		throw ExceptionBuilder.buildUnsupportedOperationException();
	}

	@Override public SQLXML createSQLXML() throws SQLException {
		throw ExceptionBuilder.buildUnsupportedOperationException();
	}

	@Override public boolean isValid(int timeout) throws SQLException {
		throw ExceptionBuilder.buildUnsupportedOperationException();
	}

	@Override public void setClientInfo(String name, String value) throws SQLClientInfoException {
		throw ExceptionBuilder.buildUnsupportedOperationException();
	}

	@Override public void setClientInfo(Properties properties) throws SQLClientInfoException {
		throw ExceptionBuilder.buildUnsupportedOperationException();
	}

	@Override public String getClientInfo(String name) throws SQLException {
		throw ExceptionBuilder.buildUnsupportedOperationException();
	}

	@Override public Properties getClientInfo() throws SQLException {
		throw ExceptionBuilder.buildUnsupportedOperationException();
	}

	@Override public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
		throw ExceptionBuilder.buildUnsupportedOperationException();
	}

	@Override public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
		throw ExceptionBuilder.buildUnsupportedOperationException();
	}

	@Override public void setSchema(String schema) throws SQLException {
		throw ExceptionBuilder.buildUnsupportedOperationException();
	}

	@Override public String getSchema() throws SQLException {
		throw ExceptionBuilder.buildUnsupportedOperationException();
	}

	@Override public void abort(Executor executor) throws SQLException {
		throw ExceptionBuilder.buildUnsupportedOperationException();
	}

	@Override public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
		throw ExceptionBuilder.buildUnsupportedOperationException();
	}

	@Override public int getNetworkTimeout() throws SQLException {
		throw ExceptionBuilder.buildUnsupportedOperationException();
	}

}
