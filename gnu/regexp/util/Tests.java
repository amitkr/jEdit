package gnu.regexp.util;
import gnu.regexp.*;

public class Tests {
  public static void check(REMatch m, String expect, int x) {
    if ((m == null) || !m.toString().equals(expect)) System.out.print("Failed");
    else System.out.print("Passed");
    System.out.println(" test #"+x);
  }

  public static void main(String[] argv) throws REException {
    RE e = null;

    e = new RE("(.*)z");
    check(e.getMatch("xxz"),"xxz",1);

    e = new RE(".*z");
    check(e.getMatch("xxz"),"xxz",2);
    
    e = new RE("(x|xy)z");
    check(e.getMatch("xz"),"xz",3);
    check(e.getMatch("xyz"),"xyz",4);

    e = new RE("(x)+z");
    check(e.getMatch("xxz"),"xxz",5);

    e = new RE("abc");
    check(e.getMatch("xyzabcdef"),"abc",6);

    e = new RE("^start.*end$");
    check(e.getMatch("start here and go to the end"),"start here and go to the end",7);

    e = new RE("(x|xy)+z");
    check(e.getMatch("xxyz"),"xxyz",8);

    e = new RE("type=([^ \t]+)[ \t]+exts=([^ \t\n\r]+)");
    check(e.getMatch("type=text/html  exts=htm,html"),"type=text/html  exts=htm,html",9);

    e = new RE("(x)\\1");
    check(e.getMatch("zxxz"),"xx", 10);

    e = new RE("(x*)(y)\\2\\1");
    check(e.getMatch("xxxyyxx"),"xxyyxx",11);

    e = new RE("[-go]+");
    check(e.getMatch("go-go"),"go-go",12);

    e = new RE("[\\w-]+");
    check(e.getMatch("go-go"),"go-go",13);

    e = new RE("^start.*?end");
    check(e.getMatch("start here and end in the middle, not the very end"),"start here and end",14);
    
    e = new RE("\\d\\s\\w\\n\\r");
    check(e.getMatch("  9\tX\n\r  "),"9\tX\n\r",15);

    e = new RE("zow",RE.REG_ICASE);
    check(e.getMatch("ZoW"),"ZoW",16);
  }
}      
    

