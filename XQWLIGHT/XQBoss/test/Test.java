import xqboss.GBLineInputStream;
import xqboss.PgnFile;

public class Test {
	public static void main(String[] args) {
		GBLineInputStream in = new GBLineInputStream(Test.class.getResourceAsStream("/xqboss/SAMPLE_S.PGN"));
		new PgnFile(in);
	}
}