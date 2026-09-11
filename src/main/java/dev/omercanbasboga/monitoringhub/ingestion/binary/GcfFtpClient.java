package dev.omercanbasboga.monitoringhub.ingestion.binary;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;

import java.io.*;
import java.time.LocalDate;
import java.util.*;

/**
 * FTP client for GCF file drops laid out as {@code /{year}/{month}/{day}/{hour}/*.gcf}
 * (a common convention for hourly sensor-station exports). Reads files
 * straight into memory and parses them with {@link GcfBinaryParser} without
 * ever touching local disk.
 *
 * <pre>
 * try (GcfFtpClient client = new GcfFtpClient("ftp.example.com", 21, "user", "pass")) {
 *     client.connect();
 *     Map&lt;String, List&lt;String&gt;&gt; byHour = client.listDay(LocalDate.of(2026, 4, 22));
 *     List&lt;GcfBinaryParser.GcfRecord&gt; records = client.parseHour(LocalDate.of(2026, 4, 22), 0);
 * }
 * </pre>
 */
public class GcfFtpClient implements Closeable {

    private final String host;
    private final int port;
    private final String user;
    private final String password;
    private final boolean passive;

    private FTPClient ftp;

    public GcfFtpClient(String host, int port, String user, String password, boolean passive) {
        this.host = host;
        this.port = port;
        this.user = user;
        this.password = password;
        this.passive = passive;
    }

    public GcfFtpClient(String host, int port, String user, String password) {
        this(host, port, user, password, true);
    }

    public void connect() throws IOException {
        ftp = new FTPClient();
        // No timeout meant a hung server could block a thread forever; cap connect/data/read.
        ftp.setConnectTimeout(10_000);
        ftp.setDefaultTimeout(10_000);
        ftp.connect(host, port);
        ftp.setSoTimeout(30_000);
        ftp.setDataTimeout(java.time.Duration.ofSeconds(30));

        int reply = ftp.getReplyCode();
        if (!FTPReply.isPositiveCompletion(reply)) {
            ftp.disconnect();
            throw new IOException("FTP server refused connection, code: " + reply);
        }
        if (!ftp.login(user, password)) {
            ftp.logout();
            throw new IOException("FTP login failed for user: " + user);
        }

        ftp.setFileType(FTP.BINARY_FILE_TYPE);
        if (passive) ftp.enterLocalPassiveMode();
        else ftp.enterLocalActiveMode();
    }

    @Override
    public void close() { disconnect(); }

    public void disconnect() {
        if (ftp == null || !ftp.isConnected()) return;
        try { ftp.logout(); ftp.disconnect(); } catch (IOException ignored) {}
    }

    public boolean isConnected() { return ftp != null && ftp.isConnected(); }

    public List<String> listYears() throws IOException { return listSubDirs("/"); }
    public List<String> listMonths(int year) throws IOException { return listSubDirs("/" + year); }
    public List<String> listDays(int year, int month) throws IOException { return listSubDirs(String.format("/%d/%02d", year, month)); }
    public List<String> listHours(int year, int month, int day) throws IOException { return listSubDirs(String.format("/%d/%02d/%02d", year, month, day)); }

    public List<FTPFile> listHourFiles(LocalDate date, int hour) throws IOException {
        return listFiles(hourPath(date, hour), ".gcf");
    }

    /** Maps every hour in the given day to the .gcf file paths found in it. */
    public Map<String, List<String>> listDay(LocalDate date) throws IOException {
        List<String> hours = listHours(date.getYear(), date.getMonthValue(), date.getDayOfMonth());
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (String hour : hours) {
            String dir = String.format("/%d/%02d/%02d/%s", date.getYear(), date.getMonthValue(), date.getDayOfMonth(), hour);
            List<String> paths = new ArrayList<>();
            for (FTPFile f : listFiles(dir, ".gcf")) paths.add(dir + "/" + f.getName());
            result.put(hour, paths);
        }
        return result;
    }

    /** Parses every .gcf file found in one hour's folder. */
    public List<GcfBinaryParser.GcfRecord> parseHour(LocalDate date, int hour) throws IOException {
        List<FTPFile> files = listHourFiles(date, hour);
        String dir = hourPath(date, hour);
        List<GcfBinaryParser.GcfRecord> records = new ArrayList<>();
        for (FTPFile f : files) {
            try {
                records.add(readAndParse(dir + "/" + f.getName()));
            } catch (IOException e) {
                // one bad file shouldn't abort the whole hour
            }
        }
        return records;
    }

    /** Reads a remote GCF file straight into memory and parses it, no local disk touch. */
    public GcfBinaryParser.GcfRecord readAndParse(String remotePath) throws IOException {
        checkConnected();
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            boolean ok = ftp.retrieveFile(remotePath, baos);
            if (!ok) throw new IOException("Could not read file: " + remotePath + " (FTP code: " + ftp.getReplyCode() + ")");
            return GcfBinaryParser.parse(new ByteArrayInputStream(baos.toByteArray()));
        }
    }

    public List<String> listSubDirs(String path) throws IOException {
        checkConnected();
        FTPFile[] entries = ftp.listFiles(path);
        List<String> dirs = new ArrayList<>();
        if (entries == null) return dirs;
        for (FTPFile f : entries) if (f.isDirectory()) dirs.add(f.getName());
        Collections.sort(dirs);
        return dirs;
    }

    public List<FTPFile> listFiles(String dir, String filter) throws IOException {
        checkConnected();
        FTPFile[] entries = ftp.listFiles(dir);
        List<FTPFile> result = new ArrayList<>();
        if (entries == null) return result;
        for (FTPFile f : entries) {
            if (f.isFile() && (filter == null || f.getName().endsWith(filter))) result.add(f);
        }
        return result;
    }

    private String hourPath(LocalDate date, int hour) {
        return String.format("/%d/%02d/%02d/%02d", date.getYear(), date.getMonthValue(), date.getDayOfMonth(), hour);
    }

    private void checkConnected() throws IOException {
        if (!isConnected()) throw new IOException("Not connected to FTP server. Call connect() first.");
    }
}
