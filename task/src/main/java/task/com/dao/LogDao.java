package task.com.dao;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.Logger;

import task.com.bean.Log;
import task.com.util.Constants;
import task.com.util.PropUtil;

public class LogDao {
	private Connection connection;
	static Logger loger = Logger.getLogger(LogDao.class.getName());

	public LogDao() {
		try {
			Class.forName(PropUtil.getJdbcDriver());
			this.connection = DriverManager.getConnection(PropUtil.getJdbcURL(), PropUtil.getDbUser(),
					PropUtil.getDbPassword());
			loger.info("Database connection created...");
		} catch (ClassNotFoundException e) {
			loger.error(e);
		} catch (SQLException e) {
			loger.error(e);
		}
	}

	public void anhilateDao() {
		if (this.connection != null) {
			try {
				this.connection.close();
				loger.info("Database connection closed...");
			} catch (SQLException e) {
				loger.error(e);				
			}
			this.connection = null;
		}
	}

	public void createTable() {
		try {
			Statement stmt = this.connection.createStatement();
			if (PropUtil.getQueryDrop() != null && PropUtil.getQueryDrop().length() > 0) {
				stmt.executeUpdate(PropUtil.getQueryDrop());
			}
			int result = stmt.executeUpdate(PropUtil.getQueryCreate());
			loger.info("Table created as per SQL :" + PropUtil.getQueryCreate());
			stmt.close();
		} catch (SQLException e) {
			loger.error(e);
		}
	}

	public void insertLog(List<Log> log) {
		ResultSet rs = null;
		PreparedStatement prepStmt = null;
		try {
			loger.info("Number of JSON logs to insert :" + log.size());
			Statement stmt = this.connection.createStatement();
			prepStmt = this.connection.prepareStatement(PropUtil.getQueryInsert());
			for (Iterator<Log> iterator = log.iterator(); iterator.hasNext();) {
				Log log2 = iterator.next();
				prepStmt.setString(1, log2.getId());
				prepStmt.setString(2, log2.getState());
				prepStmt.setString(3, log2.getType());
				prepStmt.setString(4, log2.getHost());
				prepStmt.setLong(5, log2.getTimestamp());
				prepStmt.setShort(6, log2.getAlert());
				prepStmt.executeUpdate();
			}

			loger.info("Number of JSON logs inserted :" + log.size());

			stmt = this.connection.createStatement();
			rs = stmt.executeQuery(PropUtil.getQueryAlert());
			LinkedList<Log> scsmbstgr = fetchDataForAlert(rs);

			prepStmt = this.connection.prepareStatement(PropUtil.getQueryAlertUpdate());
			loger.info("Updating 'alert' flag in database.");
			for (Iterator<Log> iterator = scsmbstgr.iterator(); iterator.hasNext();) {
				Log log2 = iterator.next();
				prepStmt.setShort(1, log2.getAlert());
				prepStmt.setInt(2, log2.getPk_id());
				prepStmt.executeUpdate();
			}
			loger.info("Updated 'alert' flag in database.");

			rs = stmt.executeQuery("select * from LOG");
			writeToFile(rs);

		} catch (SQLException e) {
			loger.error(e);
		} finally {
			if (rs != null) {
				try {
					rs.close();
					prepStmt.close();
				} catch (SQLException e) {
					loger.error(e);
				}
			}
		}
	}

	private void writeToFile(ResultSet rs) {
		BufferedWriter writer = null;
		try {
			final String path = PropUtil.getUserDir() + "\\logs_" + System.currentTimeMillis() + ".csv";
			loger.info("Preparing csv file with 'alert' flag at location : " + path);
			writer = new BufferedWriter(new FileWriter(path));
			writer.write(Constants.PK_ID + " , " + Constants.ID + " , " + Constants.STATE + " , " + Constants.TYPE
					+ " , " + Constants.HOST + " , " + Constants.TIMESTAMP + " , " + Constants.ALERT + "\n");
			while (rs.next()) {
				writer.write(rs.getInt(Constants.PK_ID) + " , " + rs.getString(Constants.ID) + " , "
						+ rs.getString(Constants.STATE) + " , " + rs.getString(Constants.TYPE) + " , "
						+ rs.getString(Constants.HOST) + " , " + rs.getString(Constants.TIMESTAMP) + " , "
						+ rs.getShort(Constants.ALERT) + "\n");

			}
			loger.info("Prepared csv file with 'alert' flag at location : " + path);
		} catch (SQLException e) {
			loger.error(e);
		} catch (IOException e) {
			loger.error(e);
		} finally {
			try {
				if (writer != null)
					writer.close();
			} catch (IOException e) {
				loger.error(e);
			}
		}

	}

	private LinkedList<Log> fetchDataForAlert(ResultSet rs) {

		LinkedList<Log> scsmbstgraS = new LinkedList<Log>();
		LinkedList<Log> scsmbstgraF = new LinkedList<Log>();
		LinkedList<Log> scsmbstgrbS = new LinkedList<Log>();
		LinkedList<Log> scsmbstgrbF = new LinkedList<Log>();
		LinkedList<Log> scsmbstgrcS = new LinkedList<Log>();
		LinkedList<Log> scsmbstgrcF = new LinkedList<Log>();
		try {
			while (rs.next()) {
				String id = rs.getString(Constants.ID);
				String state = rs.getString(Constants.STATE);
				Log log = getLog(rs);
				if (id.equals("scsmbstgra")) {
					if (state.equals(Constants.STARTED)) {
						scsmbstgraS.add(log);
					} else if (state.equals(Constants.FINISHED)) {
						scsmbstgraF.add(log);
					}
				} else if (id.equals("scsmbstgrb")) {
					if (state.equals(Constants.STARTED)) {
						scsmbstgrbS.add(log);
					} else if (state.equals(Constants.FINISHED)) {
						scsmbstgrbF.add(log);
					}
				} else if (id.equals("scsmbstgrc")) {
					if (state.equals(Constants.STARTED)) {
						scsmbstgrcS.add(log);
					} else if (state.equals(Constants.FINISHED)) {
						scsmbstgrcF.add(log);
					}
				}
			}
		} catch (SQLException e) {
			loger.error(e);
		}
		loger.info("Updating 'alert' flag.");
		LinkedList<Log> scsmbstgr = updateAlert(scsmbstgraS, scsmbstgraF);
		scsmbstgr.addAll(updateAlert(scsmbstgrbS, scsmbstgrbF));
		scsmbstgr.addAll(updateAlert(scsmbstgrcS, scsmbstgrcF));
		loger.info("Updated 'alert' flag.");
		return scsmbstgr;

	}

	private LinkedList<Log> updateAlert(LinkedList<Log> start, LinkedList<Log> finish) {

		final int size = (start.size() - finish.size()) > 0 ? finish.size() : start.size();

		for (int i = 0; i < size; i++) {
			long x = finish.get(i).getTimestamp() - start.get(i).getTimestamp();
			if (x > 4) {
				finish.get(i).setAlert((short) 1);
				start.get(i).setAlert((short) 1);
			} else {
				finish.get(i).setAlert((short) 0);
				start.get(i).setAlert((short) 0);
			}
		}
		start.addAll(finish);
		return start;
	}

	private Log getLog(ResultSet rs) {
		Log log = new Log();
		try {
			log.setPk_id(rs.getInt(Constants.PK_ID));
			log.setId(rs.getString(Constants.ID));
			log.setState(rs.getString(Constants.STATE));
			log.setType(rs.getString(Constants.STATE));
			log.setHost(rs.getString(Constants.HOST));
			log.setTimestamp(rs.getLong(Constants.TIMESTAMP));
			log.setAlert(rs.getShort(Constants.ALERT));
		} catch (SQLException e) {
			loger.error(e);
		}
		return log;
	}
}