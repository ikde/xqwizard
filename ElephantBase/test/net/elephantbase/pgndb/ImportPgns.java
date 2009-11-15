package net.elephantbase.pgndb;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import net.elephantbase.cchess.PgnReader;
import net.elephantbase.db.ConnectionPool;
import net.elephantbase.db.DBUtil;
import net.elephantbase.ecco.Ecco;
import net.elephantbase.pgndb.biz.PgnUtil;

public class ImportPgns {
	public static void main(String[] args) throws Exception {
		String sql = "INSERT INTO " + ConnectionPool.MYSQL_TABLEPRE + "pgn " +
				"(year, month, event, round, date, site, redteam, red, " +
				"blackteam, black, movelist, ecco, maxmoves, result) " +
				"VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
		File pgnDir = new File("D:\\PGNS");
		File[] files = pgnDir.listFiles();
		for (File file : files) {
			BufferedReader in = new BufferedReader(new FileReader(file));
			PgnReader pgn = new PgnReader();
			pgn.load(in);
			in.close();
			String round = pgn.getRound();
			String site = pgn.getSite();
			round = round.equals("?") ? "" : round;
			site = site.equals("?") ? "" : site;
			String date = pgn.getDate();
			String[] ss = date.split("\\.");
			int year = -1;
			try {
				year = Integer.parseInt(ss[0]);
			} catch (Exception e) {
				// Ignored
			}
			int month = -1;
			try {
				month = Integer.parseInt(ss[1]);
			} catch (Exception e) {
				// Ignored
			}
			int day = -1;
			try {
				day = Integer.parseInt(ss[2]);
			} catch (Exception e) {
				// Ignored
			}
			date = (year < 0 ? "" : year + "��");
			date += (month < 0 ? "" : month + "��");
			date += (day < 0 ? "" : day + "��");
			String moveList = pgn.getMoveList();
			int eccoId = Ecco.ecco2id(PgnUtil.parseEcco(moveList));
			DBUtil.executeUpdate(sql, Integer.valueOf(year), Integer.valueOf(month),
					pgn.getEvent(), round, date, site, pgn.getRedTeam(), pgn.getRed(),
					pgn.getBlackTeam(), pgn.getBlack(), moveList, Integer.valueOf(eccoId),
					Integer.valueOf(pgn.size()), Integer.valueOf(pgn.getResult()));
		}
	}
}