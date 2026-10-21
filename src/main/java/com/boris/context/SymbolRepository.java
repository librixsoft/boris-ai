package com.boris.context;

import com.boris.context.CodeSymbol.SymbolType;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SymbolRepository {

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
            CREATE TABLE IF NOT EXISTS symbols (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                type TEXT NOT NULL,
                parent TEXT,
                signature TEXT,
                return_type TEXT,
                file TEXT,
                line INTEGER,
                repo TEXT NOT NULL
            )
            """;

        String indexName = "CREATE INDEX IF NOT EXISTS idx_name ON symbols(name)";
        String indexParent = "CREATE INDEX IF NOT EXISTS idx_parent ON symbols(parent)";
        String indexRepo = "CREATE INDEX IF NOT EXISTS idx_repo ON symbols(repo)";
        String indexType = "CREATE INDEX IF NOT EXISTS idx_type ON symbols(type)";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createTable);
            stmt.execute(indexName);
            stmt.execute(indexParent);
            stmt.execute(indexRepo);
            stmt.execute(indexType);
        }
    }

    public static void save(CodeSymbol symbol) {
        String sql = """
            INSERT INTO symbols (name, type, parent, signature, return_type, file, line, repo)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, symbol.getName());
            ps.setString(2, symbol.getType().name());
            ps.setString(3, symbol.getParent());
            ps.setString(4, symbol.getSignature());
            ps.setString(5, symbol.getReturnType());
            ps.setString(6, symbol.getFile());
            ps.setObject(7, symbol.getLine());
            ps.setString(8, symbol.getRepo());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error saving symbol: " + e.getMessage(), e);
        }
    }

    public static void saveBatch(List<CodeSymbol> symbols) {
        if (symbols.isEmpty()) return;

        String sql = """
            INSERT INTO symbols (name, type, parent, signature, return_type, file, line, repo)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            getConnection().setAutoCommit(false);

            for (CodeSymbol symbol : symbols) {
                ps.setString(1, symbol.getName());
                ps.setString(2, symbol.getType().name());
                ps.setString(3, symbol.getParent());
                ps.setString(4, symbol.getSignature());
                ps.setString(5, symbol.getReturnType());
                ps.setString(6, symbol.getFile());
                ps.setObject(7, symbol.getLine());
                ps.setString(8, symbol.getRepo());
                ps.addBatch();
            }

            ps.executeBatch();
            getConnection().commit();
            getConnection().setAutoCommit(true);
        } catch (SQLException e) {
            throw new RuntimeException("Error saving batch: " + e.getMessage(), e);
        }
    }

    public static List<CodeSymbol> search(String query, SymbolType type, String repo) {
        StringBuilder sql = new StringBuilder("""
            SELECT id, name, type, parent, signature, return_type, file, line, repo
            FROM symbols
            WHERE LOWER(name) LIKE LOWER(?)
            """);

        List<Object> params = new ArrayList<>();
        params.add("%" + query + "%");

        if (type != null) {
            sql.append(" AND type = ?");
            params.add(type.name());
        }

        if (repo != null && !repo.isBlank()) {
            int slash = repo.indexOf('/');
            if (slash > 0 && slash < repo.length() - 1) {
                sql.append(" AND (LOWER(repo) = LOWER(?) OR LOWER(repo) = LOWER(?))");
                params.add(repo);
                params.add(repo.substring(slash + 1));
            } else {
                sql.append(" AND LOWER(repo) = LOWER(?)");
                params.add(repo);
            }
        }

        sql.append(" ORDER BY type, name LIMIT 30");

        return executeQuery(sql.toString(), params);
    }

    public static List<CodeSymbol> findMethodsOfClass(String className) {
        String sql = """
            SELECT id, name, type, parent, signature, return_type, file, line, repo
            FROM symbols
            WHERE parent LIKE ? AND type = 'METHOD'
            ORDER BY name
            LIMIT 50
            """;
        return executeQuery(sql, List.of("%" + className));
    }

    public static List<CodeSymbol> findExact(String name) {
        if (name == null || name.isBlank()) {
            return new ArrayList<>();
        }
        String sql = """
            SELECT id, name, type, parent, signature, return_type, file, line, repo
            FROM symbols
            WHERE LOWER(name) = LOWER(?) OR parent = ? OR parent LIKE ?
            ORDER BY type, name
            LIMIT 30
            """;
        return executeQuery(sql, List.of(name, name, "%." + name));
    }

    public static List<CodeSymbol> findSymbolsByFile(String file) {
        if (file == null || file.isBlank()) {
            return new ArrayList<>();
        }
        String sql = """
            SELECT id, name, type, parent, signature, return_type, file, line, repo
            FROM symbols
            WHERE file = ?
            ORDER BY type, name
            LIMIT 60
            """;
        return executeQuery(sql, List.of(file));
    }

    public static List<CodeSymbol> findAllClasses(String repo) {
        String sql = """
            SELECT id, name, type, parent, signature, return_type, file, line, repo
            FROM symbols
            WHERE type IN ('CLASS', 'INTERFACE', 'ENUM')
            """ + (repo != null ? " AND repo = ?" : "") + """
            ORDER BY name
            """;

        return repo != null ? executeQuery(sql, List.of(repo)) : executeQuery(sql, List.of());
    }

    public static java.util.Map<String, Integer> methodCountByClass(String repo) {
        String sql = """
            SELECT parent, COUNT(*) AS method_count
            FROM symbols
            WHERE repo = ? AND type = 'METHOD' AND parent IS NOT NULL
            GROUP BY parent
            ORDER BY method_count DESC
            LIMIT 10
            """;
        java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, repo);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                counts.put(rs.getString("parent"), rs.getInt("method_count"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error contando métodos por clase: " + e.getMessage(), e);
        }
        return counts;
    }

    public static boolean isIndexed(String repo) {
        String sql = "SELECT COUNT(*) FROM symbols WHERE repo = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, repo);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public static int countByRepo(String repo) {
        String sql = "SELECT COUNT(*) FROM symbols WHERE repo = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, repo);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            return 0;
        }
    }

    public static void deleteByRepo(String repo) {
        String sql = "DELETE FROM symbols WHERE repo = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, repo);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting repo: " + e.getMessage(), e);
        }
    }

    public static void deleteByFile(String file) {
        String sql = "DELETE FROM symbols WHERE file = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, file);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting file: " + e.getMessage(), e);
        }
    }

    public static int totalCount() {
        String sql = "SELECT COUNT(*) FROM symbols";
        try (Statement stmt = getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            return 0;
        }
    }

    public static List<String> listRepos() {
        String sql = "SELECT DISTINCT repo FROM symbols ORDER BY repo";
        List<String> repos = new ArrayList<>();
        try (Statement stmt = getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                repos.add(rs.getString("repo"));
            }
        } catch (SQLException e) {
            // ignore
        }
        return repos;
    }

    private static List<CodeSymbol> executeQuery(String sql, List<Object> params) {
        List<CodeSymbol> results = new ArrayList<>();

        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                CodeSymbol symbol = new CodeSymbol();
                symbol.setId(rs.getLong("id"));
                symbol.setName(rs.getString("name"));
                symbol.setType(SymbolType.valueOf(rs.getString("type")));
                symbol.setParent(rs.getString("parent"));
                symbol.setSignature(rs.getString("signature"));
                symbol.setReturnType(rs.getString("return_type"));
                symbol.setFile(rs.getString("file"));
                symbol.setLine(rs.getObject("line", Integer.class));
                symbol.setRepo(rs.getString("repo"));
                results.add(symbol);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Query error: " + e.getMessage(), e);
        }

        return results;
    }
}
