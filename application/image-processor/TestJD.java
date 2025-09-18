
public class TestJD {
    public static void main(String[] args) {
        int year = 2000, month = 2, day = 29;
        if (month <= 2) {
            year -= 1;
            month += 12;
        }
        int a = year / 100;
        int b = 2 - a + (a / 4);
        double jd = Math.floor(365.25 * (year + 4716)) + 
                   Math.floor(30.6001 * (month + 1)) + 
                   day + b - 1524.5;
        System.out.println("JD for 2000-02-29: " + jd);
        System.out.println("Is in range [2451500, 2451600]: " + (jd > 2451500.0 && jd < 2451600.0));
    }
}

