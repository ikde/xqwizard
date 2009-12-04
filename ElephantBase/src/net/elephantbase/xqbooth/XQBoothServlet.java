package net.elephantbase.xqbooth;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedList;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.elephantbase.db.DBUtil;
import net.elephantbase.db.Row;
import net.elephantbase.db.RowCallback;
import net.elephantbase.users.biz.EventLog;
import net.elephantbase.users.biz.UserData;
import net.elephantbase.users.biz.Users;
import net.elephantbase.util.EasyDate;
import net.elephantbase.util.Integers;
import net.elephantbase.util.Logger;
import net.elephantbase.util.Servlets;

public class XQBoothServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;

	private static final int INCORRECT = 0;
	private static final int NO_RETRY = -1;
	private static final int INTERNAL_ERROR = -2;

	private static LinkedList<ScoreEntry> queue = new LinkedList<ScoreEntry>();

	public static synchronized ArrayList<ScoreEntry> getRecentList() {
		return new ArrayList<ScoreEntry>(queue);
	}

	private static synchronized void addEntry(ScoreEntry entry) {
		for (ScoreEntry old : queue) {
			if (old.getUid() == entry.getUid()) {
				queue.remove(old);
				break;
			}
		}
		if (queue.size() == 10) {
			queue.removeLast();
		}
		queue.addFirst(entry);
	}

	private static int login(String[] username, String password, String[] cookie) {
		if (username == null || username.length == 0 || username[0] == null) {
			return INTERNAL_ERROR;
		}

		String gbk, big5;
		try {
			byte[] b = username[0].getBytes("ISO-8859-1");
			gbk = new String(b, "GBK");
			big5 = new String(b, "BIG5");
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		// 1. Login with Cookie
		String[] username_ = new String[1];
		String[] cookie_ = {password};
		int uid = Users.loginCookie(cookie_, username_, null);
		if (uid > 0) {
			if (username_[0].equals(gbk)) {
				username[0] = gbk;
			} else if (username_[0].equals(big5)) {
				username[0] = big5;
			} else {
				uid = 0;
			}
			if (uid > 0) {
				if (cookie != null && cookie.length > 0) {
					cookie[0] = cookie_[0];
				}
				return uid;
			}
		}

		// 2. Get "uid" and Check if "noretry"
		String sql = "SELECT uc_members.uid, retrycount, retrytime, password, salt " +
				"FROM uc_members LEFT JOIN xq_retry USING (uid) WHERE username = ?";
		Row row = DBUtil.query(5, sql, gbk);
		username[0] = gbk;
		if (row.empty()) {
			row = DBUtil.query(5, sql, big5);
			username[0] = big5;
		}
		if (row.error()) {
			return INTERNAL_ERROR;
		}
		if (row.empty()) {
			return INCORRECT;
		}
		uid = row.getInt(1);
		int retryCount = row.getInt(2);
		if (retryCount > 0 && EasyDate.currTimeSec() < row.getInt(3)) {
			return NO_RETRY;
		}

		// 3. Try Login
		String key = row.getString(4);
		String salt = row.getString(5);
		if (Users.getKey(password, salt).equals(key)) {
			if (cookie != null && cookie.length > 0) {
				cookie[0] = Users.addCookie(uid);
			}
			sql = "DELETE FROM xq_retry WHERE uid = ?";
			DBUtil.update(sql, Integer.valueOf(uid));
			return uid;
		}

		// 4. Update "retry" table
		if (retryCount == 0) {
			sql = "INSERT INTO xq_retry (uid, retrycount, retrytime) " +
					"VALUES (?, 1, 0)";
			DBUtil.update(sql, Integer.valueOf(uid));
			return INCORRECT;
		}
		if (retryCount < 5) {
			sql = "UPDATE xq_retry SET retrycount = retrycount + 1 WHERE uid = ?";
			DBUtil.update(sql, Integer.valueOf(uid));
			return INCORRECT;
		}
		sql = "UPDATE xq_retry SET retrycount = 1, retrytime = ? WHERE uid = ?";
		DBUtil.update(sql, Integer.valueOf(EasyDate.currTimeSec() + 300),
				Integer.valueOf(uid));
		return NO_RETRY;
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
		doGet(req, resp);
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
		String act = req.getPathInfo();
		if (act == null) {
			return;
		}
		while (act.startsWith("/")) {
			act = act.substring(1);
		}
		if (act.equals("ranklist")) {
			String sql = "SELECT username, score FROM xq_rank LEFT JOIN " +
					"uc_members USING (uid) ORDER BY rank LIMIT 100";
			final PrintWriter out;
			resp.setCharacterEncoding("GBK");
			try {
				out = resp.getWriter();
			} catch (Exception e) {
				Logger.severe(e);
				return;
			}
			DBUtil.query(2, new RowCallback() {
				@Override
				public boolean onRow(Row row) {
					out.print(row.getInt(2) + "|" + row.getString(1) + "\r\n");
					return true;
				}
			}, sql);
			return;
		}

		String[] username = {req.getHeader("Login-UserName")};
		String password = req.getHeader("Login-Password");
		String[] cookie = new String[1];
		int uid = login(username, password, cookie);
		if (uid == INTERNAL_ERROR) {
			resp.setHeader("Login-Result", "internal-error");
			return;
		}
		if (uid == NO_RETRY) {
			resp.setHeader("Login-Result", "noretry");
			return;
		}
		if (uid == INCORRECT) {
			resp.setHeader("Login-Result", "error");
			return;
		}
		resp.setHeader("Login-Cookie", cookie[0]);

		String strStage = req.getParameter("stage");
		if (strStage == null) {
			strStage = req.getParameter("score");
		}
		int stage = Integers.parseInt(strStage);
		String ip = Servlets.getRemoteHost(req);
		UserData user = new UserData(uid, ip);

		if (false) {
	    	// Code Style
	    } else if (act.equals("querypoints")) {
			resp.setHeader("Login-Result", "ok " +
					user.getPoints() + "|" + user.getCharged());
		} else if (act.equals("queryscore")) {
			resp.setHeader("Login-Result", "ok " + user.getScore());
		} else if (act.equals("queryrank")) {
			String sql = "SELECT rank, score FROM xq_rank WHERE uid = ?";
			Row row = DBUtil.query(2, sql, Integer.valueOf(uid));
			int rankToday = row.getInt(1, 0);
			int scoreToday = 0, rankYesterday = 0;
			if (rankToday > 0) {
				scoreToday = row.getInt(2);
				sql = "SELECT rank FROM xq_rank0 WHERE uid = ?";
				row = DBUtil.query(1, sql, Integer.valueOf(uid));
				rankYesterday = row.getInt(1, 0);
			}
			resp.setHeader("Login-Result", "ok " + scoreToday + "|" +
					rankToday + "|" + rankYesterday);
		} else if (act.equals("save")) {
			if (stage > user.getScore()) {
				String sql = "UPDATE xq_user SET score = ? WHERE uid = ?";
				DBUtil.update(sql, Integer.valueOf(stage), Integer.valueOf(uid));
				resp.setHeader("Login-Result", "ok");
				addEntry(new ScoreEntry(uid, username[0], stage));
				EventLog.log(uid, ip, EventLog.SAVE, stage);
			} else {
				resp.setHeader("Login-Result", "nosave");
			}
		} else if (act.equals("hint")) {
			if (stage < 500) {
				resp.setHeader("Login-Result", "ok");
			} else if (user.getPoints() < 10 && !user.isPlatinum()) {
				resp.setHeader("Login-Result", "nopoints");
			} else {
				if (!user.isPlatinum()) {
					String sql = "UPDATE xq_user SET points = " +
							"points - 10 WHERE uid = ?";
					DBUtil.update(sql, Integer.valueOf(uid));
				}
				resp.setHeader("Login-Result", "ok");
				EventLog.log(uid, ip, EventLog.HINT, stage);
			}
		} else if (act.equals("retract")) {
			if (stage < 500) {
				resp.setHeader("Login-Result", "ok");
			} else if (user.getPoints() < 1 && !user.isPlatinum()) {
				resp.setHeader("Login-Result", "nopoints");
			} else {
				if (!user.isPlatinum()) {
					String sql = "UPDATE xq_user SET points = " +
							"points - 1 WHERE uid = ?";
					DBUtil.update(sql, Integer.valueOf(uid));
				}
				resp.setHeader("Login-Result", "ok");
				EventLog.log(uid, ip, EventLog.RETRACT, stage);
			}
		}
	}
}