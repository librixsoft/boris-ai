package com.boris.context;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ContentRepository {

    public record DocChunk(String repo, String path, int chunkIndex, String title, String content) {}

    public record DocResult(String repo, String path, int chunkIndex, String title, String content) {}

    private static final String MEMORY_URL = "jdbc:sqlite::memory:";
    private static Connection connection;
    private static final Object LOCK = new Object();

    private static Connection getConnection() throws SQLException {
        synchronized (LOCK) {
            if (connection == null || connection.isClosed()) {
                connection = DriverManager.getConnection(MEMORY_URL);
                initSchema();
            }
            return connection;
        }
    }

    private static void initSchema() throws SQLException {
        String createTable = """
            CREATE TABLE IF NOT EXISTS docs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                repo TEXT NOT NULL,
                path TEXT NOT NULL,
                chunk_index INTEGER NOT NULL,
                title TEXT,
                content TEXT NOT NULL
            )
            """;

        String indexRepo = "CREATE INDEX IF NOT EXISTS idx_docs_repo ON docs(repo)";
        String indexPath = "CREATE INDEX IF NOT EXISTS idx_docs_path ON docs(path)";
        String indexContent = "CREATE INDEX IF NOT EXISTS idx_docs_content ON docs(content)";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createTable);
            stmt.execute(indexRepo);
            stmt.execute(indexPath);
            stmt.execute(indexContent);
        }
    }

    public static void saveBatch(List<DocChunk> chunks) {
        if (chunks.isEmpty()) return;

        String sql = """
            INSERT INTO docs (repo, path, chunk_index, title, content)
            VALUES (?, ?, ?, ?, ?)
            """;

        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            getConnection().setAutoCommit(false);

            for (DocChunk chunk : chunks) {
                ps.setString(1, chunk.repo());
                ps.setString(2, chunk.path());
                ps.setInt(3, chunk.chunkIndex());
                ps.setString(4, chunk.title());
                ps.setString(5, chunk.content());
                ps.addBatch();
            }

            ps.executeBatch();
            getConnection().commit();
            getConnection().setAutoCommit(true);
        } catch (SQLException e) {
            throw new RuntimeException("Error saving content batch: " + e.getMessage(), e);
        }
    }

    public static List<DocResult> search(String query, String repo) {
        StringBuilder sql = new StringBuilder("""
            SELECT repo, path, chunk_index, title, content
            FROM docs
            WHERE LOWER(content) LIKE LOWER(?) OR LOWER(title) LIKE LOWER(?)
            """);

        List<Object> params = new ArrayList<>();
        params.add("%" + query + "%");
        params.add("%" + query + "%");

        if (repo != null && !repo.isBlank()) {
            sql.append(filterFor(repo));
            params.add(repo);
        }

        sql.append(" ORDER BY path, chunk_index LIMIT 15");

        return queryDocs(sql.toString(), params);
    }

    private static String filterFor(String repo) {
        int slash = repo.indexOf('/');
        if (slash > 0 && slash < repo.length() - 1) {
            return " AND (LOWER(repo) = LOWER(?) OR LOWER(repo) = LOWER(?))";
        }
        return " AND LOWER(repo) = LOWER(?)";
    }

    public static List<DocResult> readByPath(String repo, String path) {
        String sql = """
            SELECT repo, path, chunk_index, title, content
            FROM docs
            WHERE LOWER(path) = LOWER(?)
            """ + (repo != null && !repo.isBlank() ? filterFor(repo) : "") + """
            ORDER BY path, chunk_index
            LIMIT 100
            """;

        List<Object> params = new ArrayList<>();
        params.add(path);
        if (repo != null && !repo.isBlank()) {
            params.add(repo);
            int slash = repo.indexOf('/');
            if (slash > 0 && slash < repo.length() - 1) {
                params.add(repo.substring(slash + 1));
            }
        }

        return queryDocs(sql, params);
    }

    public static List<String> listFiles(String repo) {
        String sql = "SELECT DISTINCT path FROM docs" +
            (repo != null && !repo.isBlank() ? " WHERE LOWER(repo) = LOWER(?)" : "") +
            " ORDER BY path";

        List<String> paths = new ArrayList<>();
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            if (repo != null && !repo.isBlank()) {
                ps.setObject(1, repo);
            }
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                paths.add(rs.getString("path"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error listing doc files: " + e.getMessage(), e);
        }
        return paths;
    }

    public static boolean isIndexed(String repo) {
        String sql = "SELECT COUNT(*) FROM docs WHERE LOWER(repo) = LOWER(?)";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setObject(1, repo);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public static int countByRepo(String repo) {
        String sql = "SELECT COUNT(*) FROM docs WHERE LOWER(repo) = LOWER(?)";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setObject(1, repo);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            return 0;
        }
    }

    public static void deleteByRepo(String repo) {
        String sql = "DELETE FROM docs WHERE LOWER(repo) = LOWER(?)";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setObject(1, repo);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting content repo: " + e.getMessage(), e);
        }
    }

    public static int totalDocCount() {
        String sql = "SELECT COUNT(*) FROM docs";
        try (Statement stmt = getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            return 0;
        }
    }

    public static List<String> listRepos() {
        String sql = "SELECT DISTINCT repo FROM docs ORDER BY repo";
        List<String> repos = new ArrayList<>();
        try (Statement stmt = getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                repos.add(rs.getString("repo"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error listing content repos: " + e.getMessage(), e);
        }
        return repos;
    }

    public static List<String> listDocsRepos() {
        return listRepos();
    }

    private static List<DocResult> queryDocs(String sql, List<Object> params) {
        List<DocResult> results = new ArrayList<>();

        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                results.add(new DocResult(
                    rs.getString("repo"),
                    rs.getString("path"),
                    rs.getInt("chunk_index"),
                    rs.getString("title"),
                    rs.getString("content")
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Content query error: " + e.getMessage(), e);
        }

        return results;
    }
}
