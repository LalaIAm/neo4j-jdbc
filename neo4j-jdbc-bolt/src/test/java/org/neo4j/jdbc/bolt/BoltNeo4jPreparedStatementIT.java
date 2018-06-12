/*
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
 * Created on 25/03/16
 */
package org.neo4j.jdbc.bolt;

import org.junit.*;
import org.junit.runners.MethodSorters;
import org.neo4j.graphdb.Result;
import org.neo4j.jdbc.bolt.data.StatementData;

import java.sql.*;
import java.util.Enumeration;

import static org.junit.Assert.*;

/**
 * @author AgileLARUS
 * @since 3.0.0
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class BoltNeo4jPreparedStatementIT {

	@ClassRule public static Neo4jBoltRule neo4j = new Neo4jBoltRule();

	/*------------------------------*/
	/*          executeQuery        */
	/*------------------------------*/

	@Before
	public void cleanDB(){
		neo4j.getGraphDatabase().execute(StatementData.STATEMENT_CLEAR_DB);
	}

	// 'a_' only to force execution at first position
	@Test public void a_warmup() throws SQLException {


		// WARM UP

		long t0 = System.currentTimeMillis();
		boolean driverLoaded = false;
		while (!driverLoaded && System.currentTimeMillis() - t0 < 10_000){
			Enumeration<Driver> drivers = DriverManager.getDrivers();
			while (drivers.hasMoreElements()){
				if(BoltDriver.class.equals(drivers.nextElement().getClass())){
					driverLoaded = true;
				}
			}
		}


		Driver driver = DriverManager.getDriver("jdbc:neo4j:" + neo4j.getBoltUrl() + "?nossl");
		Assert.assertEquals(BoltDriver.class,driver.getClass());
	}

	@Test public void executeQueryShouldExecuteAndReturnCorrectData() throws SQLException {
		neo4j.getGraphDatabase().execute(StatementData.STATEMENT_CREATE_TWO_PROPERTIES);
		Connection connection = getConnection();
		PreparedStatement statement = connection.prepareStatement(StatementData.STATEMENT_MATCH_ALL_STRING_PARAMETRIC);
		statement.setString(1, "test");
		ResultSet rs = statement.executeQuery();
		
		assertTrue(rs.next());
		assertEquals("testAgain", rs.getString(1));
		assertFalse(rs.next());
		connection.close();
		neo4j.getGraphDatabase().execute(StatementData.STATEMENT_CREATE_TWO_PROPERTIES_REV);
	}

	@Test public void executeQueryWithNamedParamShouldExecuteAndReturnCorrectData() throws SQLException {
		neo4j.getGraphDatabase().execute(StatementData.STATEMENT_CREATE_TWO_PROPERTIES);
		Connection connection = getConnection();
		PreparedStatement statement = connection.prepareStatement(StatementData.STATEMENT_MATCH_ALL_STRING_PARAMETRIC_NAMED);
		statement.setString(1, "test");
		ResultSet rs = statement.executeQuery();

		assertTrue(rs.next());
		assertEquals("testAgain", rs.getString(1));
		assertFalse(rs.next());
		connection.close();
		neo4j.getGraphDatabase().execute(StatementData.STATEMENT_CREATE_TWO_PROPERTIES_REV);
	}

	/*------------------------------*/
	/*         executeUpdate        */
	/*------------------------------*/
	@Test public void executeUpdateShouldExecuteAndReturnCorrectData() throws SQLException {
		Connection connection = getConnection();
		PreparedStatement statement = connection.prepareStatement(StatementData.STATEMENT_CREATE_TWO_PROPERTIES_PARAMETRIC);
		statement.setString(1, "test1");
		statement.setString(2, "test2");
		int lines = statement.executeUpdate();
		assertEquals(1, lines);

		lines = statement.executeUpdate();
		assertEquals(1, lines);

		statement = connection.prepareStatement(StatementData.STATEMENT_CREATE_TWO_PROPERTIES_PARAMETRIC_REV);
		statement.setString(1, "test1");
		statement.setString(2, "test2");
		lines = statement.executeUpdate();
		assertEquals(2, lines);

		connection.close();
	}

	/*------------------------------*/
	/*            execute           */
	/*------------------------------*/
	@Test public void executeShouldExecuteAndReturnTrue() throws SQLException {
		neo4j.getGraphDatabase().execute(StatementData.STATEMENT_CREATE_TWO_PROPERTIES);
		Connection connection = getConnection();
		PreparedStatement statement = connection.prepareStatement(StatementData.STATEMENT_MATCH_ALL_STRING_PARAMETRIC);
		statement.setString(1, "test");
		boolean result = statement.execute();
		assertTrue(result);
		ResultSet resultSet = statement.getResultSet();
		assertTrue(resultSet.next());
		assertEquals("testAgain", resultSet.getString("n.surname"));
		assertFalse(resultSet.next());
		resultSet.close();
		statement.close();
		connection.close();
	}

	@Test public void executeShouldExecuteAndReturnFalse() throws SQLException {
		Connection connection = getConnection();
		PreparedStatement statement = connection.prepareStatement(StatementData.STATEMENT_CREATE_TWO_PROPERTIES_PARAMETRIC);
		statement.setString(1, "test1");
		statement.setString(2, "test2");

		boolean result = statement.execute();
		assertFalse(result);

		statement = connection.prepareStatement(StatementData.STATEMENT_CREATE_TWO_PROPERTIES_PARAMETRIC_REV);
		statement.setString(1, "test1");
		statement.setString(2, "test2");

		result = statement.execute();
		assertFalse(result);

		connection.close();
	}

	/*------------------------------*/
	/*         executeBatch         */
	/*------------------------------*/
	@Test public void executeBatchShouldWork() throws SQLException {
		Connection connection = getConnection();
		PreparedStatement statement = connection.prepareStatement(StatementData.STATEMENT_CREATE_TWO_PROPERTIES_PARAMETRIC);
		connection.setAutoCommit(true);
		statement.setString(1, "test1");
		statement.setString(2, "test2");
		statement.addBatch();
		statement.setString(1, "test3");
		statement.setString(2, "test4");
		statement.addBatch();
		statement.setString(1, "test5");
		statement.setString(2, "test6");
		statement.addBatch();

		int[] result = statement.executeBatch();

		assertArrayEquals(new int[]{1, 1, 1}, result);

		connection.close();

	}

	@Test public void executeBatchShouldWorkWhenError() throws SQLException {
		Connection connection = getConnection();
		connection.setAutoCommit(true);
		PreparedStatement statement = connection.prepareStatement("wrong cypher statement ?");
		statement.setString(1, "test1");
		statement.addBatch();
		statement.setString(1, "test3");
		statement.addBatch();
		statement.setString(1, "test5");
		statement.addBatch();

		try {
			statement.executeBatch();
			fail();
		} catch (BatchUpdateException e){
			assertArrayEquals(new int[0], e.getUpdateCounts());
		}

		connection.close();

	}

	@Test public void executeBatchShouldWorkWithTransaction() throws SQLException {
		Connection connection = getConnection();
		PreparedStatement statement = connection.prepareStatement(StatementData.STATEMENT_CREATE_TWO_PROPERTIES_PARAMETRIC);
		connection.setAutoCommit(false);
		statement.setString(1, "test1");
		statement.setString(2, "test2");
		statement.addBatch();
		statement.setString(1, "test3");
		statement.setString(2, "test4");
		statement.addBatch();
		statement.setString(1, "test5");
		statement.setString(2, "test6");
		statement.addBatch();

		Result res = neo4j.getGraphDatabase().execute(StatementData.STATEMENT_COUNT_NODES);
		while(res.hasNext()){
			assertEquals(0L, res.next().get("total"));
		}

		int[] result = statement.executeBatch();

		assertArrayEquals(new int[]{1, 1, 1}, result);

		connection.commit();

		res = neo4j.getGraphDatabase().execute(StatementData.STATEMENT_COUNT_NODES);
		while(res.hasNext()){
			assertEquals(3L, res.next().get("total"));
		}

		connection.close();
	}

	private Connection getConnection() throws SQLException {
		return DriverManager.getConnection("jdbc:neo4j:" + neo4j.getBoltUrl() + "?nossl");
	}

	/*------------------------------*/
	/*            MoreResult        */
	/*------------------------------*/
	@Test public void testMoreResultWithOneResultSet() throws SQLException {
		neo4j.getGraphDatabase().execute(StatementData.STATEMENT_CREATE_TWO_PROPERTIES);
		Connection connection = getConnection();
		PreparedStatement statement = connection.prepareStatement(StatementData.STATEMENT_MATCH_ALL_STRING_PARAMETRIC);
		statement.setString(1, "test");
		boolean result = statement.execute();

		assertTrue(statement.getMoreResults());

		ResultSet resultSet = statement.getResultSet();
		assertNotNull(resultSet);

		assertFalse(statement.getMoreResults());

		ResultSet resultSet2 = statement.getResultSet();
		assertNull(resultSet2);

		resultSet.close();
		statement.close();
		connection.close();
	}

	@Test public void testMoreResultWithNoResultSet() throws SQLException {
		Connection connection = getConnection();
		PreparedStatement statement = connection.prepareStatement(StatementData.STATEMENT_CREATE_TWO_PROPERTIES_PARAMETRIC);
		statement.setString(1, "testName");
		statement.setString(2, "testSurname");
		boolean result = statement.execute();

		assertFalse(statement.getMoreResults());

		ResultSet resultSet = statement.getResultSet();
		assertNull(resultSet);

		assertFalse(statement.getMoreResults());

		statement.close();
		connection.close();
	}

	/*------------------------------*/
	/*            UpdateCount       */
	/*------------------------------*/
	@Test public void testUpdateCountWithCreate() throws SQLException {
		Connection connection = getConnection();
		PreparedStatement statement = connection.prepareStatement(StatementData.STATEMENT_CREATE_TWO_PROPERTIES_PARAMETRIC);
		statement.setString(1, "test1");
		statement.setString(2, "test2");

		boolean result = statement.execute();
		assertFalse(result);
		assertEquals(1, statement.getUpdateCount());

		statement.close();
		connection.close();
	}

	@Test public void testUpdateCountWithUpdate() throws SQLException {
		neo4j.getGraphDatabase().execute(StatementData.STATEMENT_CREATE_TWO_PROPERTIES);
		Connection connection = getConnection();
		PreparedStatement statement = connection.prepareStatement(StatementData.STATEMENT_UPDATE_NODES_PARAM);
		statement.setString(1, "test");

		boolean result = statement.execute();
		assertFalse(result);
		assertEquals(1, statement.getUpdateCount());

		statement.close();
		connection.close();
	}

	@Test public void testUpdateCountWithReturn() throws SQLException {
		neo4j.getGraphDatabase().execute(StatementData.STATEMENT_CREATE_TWO_PROPERTIES);
		Connection connection = getConnection();
		PreparedStatement statement = connection.prepareStatement(StatementData.STATEMENT_MATCH_ALL_STRING_PARAMETRIC_NAMED);
		statement.setString(1, "test");

		boolean result = statement.execute();
		assertTrue(result);
		assertEquals(-1, statement.getUpdateCount());

		statement.close();
		connection.close();
	}
}

