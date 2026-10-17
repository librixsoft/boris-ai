package com.boris.context;

public class CodeSymbol {

    public enum SymbolType {
        CLASS, INTERFACE, METHOD, CONSTRUCTOR, FIELD, ENUM
    }

    private Long id;
    private String name;
    private SymbolType type;
    private String parent;
    private String signature;
    private String returnType;
    private String file;
    private Integer line;
    private String repo;

    public CodeSymbol() {}

    public CodeSymbol(String name, SymbolType type, String parent, String signature,
                      String returnType, String file, Integer line, String repo) {
        this.name = name;
        this.type = type;
        this.parent = parent;
        this.signature = signature;
        this.returnType = returnType;
        this.file = file;
        this.line = line;
        this.repo = repo;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public SymbolType getType() { return type; }
    public void setType(SymbolType type) { this.type = type; }

    public String getParent() { return parent; }
    public void setParent(String parent) { this.parent = parent; }

    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }

    public String getReturnType() { return returnType; }
    public void setReturnType(String returnType) { this.returnType = returnType; }

    public String getFile() { return file; }
    public void setFile(String file) { this.file = file; }

    public Integer getLine() { return line; }
    public void setLine(Integer line) { this.line = line; }

    public String getRepo() { return repo; }
    public void setRepo(String repo) { this.repo = repo; }

    @Override
    public String toString() {
        return type + " " + name + " in " + parent;
    }
}
