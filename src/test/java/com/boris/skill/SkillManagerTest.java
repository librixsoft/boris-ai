package com.boris.skill;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SkillManagerTest {

    @TempDir
    Path tempDir;

    private Path skillsDir;
    private Path workspaceDir;
    private SkillLoader skillLoader;
    private SkillExecutor skillExecutor;

    @BeforeEach
    void setUp() throws IOException {
        skillsDir = tempDir.resolve("skills");
        workspaceDir = tempDir.resolve("workspace");
        Files.createDirectories(skillsDir);
        Files.createDirectories(workspaceDir);
        skillLoader = new SkillLoader(skillsDir);
        skillExecutor = new SkillExecutor(workspaceDir);
    }

    @Test
    void testLoadEmptySkillsDir() {
        SkillManager manager = new SkillManager(skillLoader, skillExecutor);
        assertTrue(manager.getSkills().isEmpty());
    }

    @Test
    void testLoadSkillFromFile() throws IOException {
        String skillContent = """
            # Test Skill

            ## Metadata

            ```yaml
            name: testSkill
            description: Una skill de prueba
            ```

            ## Comando

            ```bash
            echo "Hello"
            ```
            """;

        Files.writeString(skillsDir.resolve("TEST.md"), skillContent);

        SkillManager manager = new SkillManager(skillLoader, skillExecutor);

        assertEquals(1, manager.getSkills().size());
        assertTrue(manager.hasSkill("testSkill"));

        Skill skill = manager.getSkill("testSkill");
        assertNotNull(skill);
        assertEquals("testSkill", skill.getName());
        assertEquals("Una skill de prueba", skill.getDescription());
        assertEquals("echo \"Hello\"", skill.getCommand());
    }

    @Test
    void testSkillNotFound() {
        SkillManager manager = new SkillManager(skillLoader, skillExecutor);

        assertFalse(manager.hasSkill("noExiste"));
        assertNull(manager.getSkill("noExiste"));
    }

    @Test
    void testExecuteSkill() throws IOException {
        String skillContent = """
            # Echo Skill

            ## Metadata

            ```yaml
            name: echoSkill
            description: Echo test
            ```

            ## Comando

            ```bash
            echo "skill ejecutada"
            ```
            """;

        Files.writeString(skillsDir.resolve("ECHO.md"), skillContent);

        SkillManager manager = new SkillManager(skillLoader, skillExecutor);

        List<String> output = new ArrayList<>();
        manager.execute("echoSkill", line -> output.add(line));

        assertFalse(output.isEmpty());
        assertTrue(output.stream().anyMatch(line -> line.contains("skill ejecutada")));
    }

    @Test
    void testExecuteSkillWithArgs() throws IOException {
        String skillContent = """
            # Args Skill

            ## Metadata

            ```yaml
            name: argsSkill
            description: Test con args
            ```

            ## Parametros

            | Nombre | Tipo | Requerido | Descripcion |
            |--------|------|-----------|-------------|
            | message | string | no | Mensaje |

            ## Comando

            ```bash
            echo "Mensaje: ${SKILL_MESSAGE:-default}"
            ```
            """;

        Files.writeString(skillsDir.resolve("ARGS.md"), skillContent);

        SkillManager manager = new SkillManager(skillLoader, skillExecutor);

        List<String> output = new ArrayList<>();
        manager.execute("argsSkill", Map.of("message", "hola mundo"), line -> output.add(line));

        assertFalse(output.isEmpty());
        assertTrue(output.stream().anyMatch(line -> line.contains("hola mundo")));
    }

    @Test
    void testExecuteNonExistentSkill() {
        SkillManager manager = new SkillManager(skillLoader, skillExecutor);

        List<String> output = new ArrayList<>();
        manager.execute("noExiste", line -> output.add(line));

        assertFalse(output.isEmpty());
        assertTrue(output.get(0).contains("error"));
    }

    @Test
    void testReloadSkills() throws IOException {
        SkillManager manager = new SkillManager(skillLoader, skillExecutor);
        assertTrue(manager.getSkills().isEmpty());

        String skillContent = """
            ## Metadata
            name: newSkill
            description: Nueva skill

            ## Comando
            ```bash
            echo "new"
            ```
            """;

        Files.writeString(skillsDir.resolve("NEW.md"), skillContent);

        manager.reload();

        assertEquals(1, manager.getSkills().size());
        assertTrue(manager.hasSkill("newSkill"));
    }

    @Test
    void testSkillLoaderParseParams() throws IOException {
        String skillContent = """
            # Params Skill

            ## Metadata

            ```yaml
            name: paramsSkill
            description: Skill con parametros
            ```

            ## Parametros

            | Nombre | Tipo | Requerido | Descripcion |
            |--------|------|-----------|-------------|
            | input | string | si | Archivo de entrada |
            | output | string | no | Archivo de salida |

            ## Comando

            ```bash
            cat $SKILL_INPUT > $SKILL_OUTPUT
            ```
            """;

        Files.writeString(skillsDir.resolve("PARAMS.md"), skillContent);

        SkillManager manager = new SkillManager(skillLoader, skillExecutor);
        Skill skill = manager.getSkill("paramsSkill");

        assertNotNull(skill);
        assertNotNull(skill.getParams());
        assertEquals(2, skill.getParams().size());

        assertTrue(skill.getParams().containsKey("input"));
        assertTrue(skill.getParams().get("input").isRequired());

        assertTrue(skill.getParams().containsKey("output"));
        assertFalse(skill.getParams().get("output").isRequired());
    }

    @Test
    void testIgnoreAgentMdFile() throws IOException {
        String agentContent = "# Agent instructions - should be ignored";
        String skillContent = """
            ## Metadata
            name: realSkill
            description: Real skill

            ## Comando
            ```bash
            echo "real"
            ```
            """;

        Files.writeString(skillsDir.resolve("AGENT.md"), agentContent);
        Files.writeString(skillsDir.resolve("REAL.md"), skillContent);

        SkillManager manager = new SkillManager(skillLoader, skillExecutor);

        assertEquals(1, manager.getSkills().size());
        assertTrue(manager.hasSkill("realSkill"));
    }

    @Test
    void testCaseInsensitiveSkillLookup() throws IOException {
        String skillContent = """
            ## Metadata
            name: MySkill
            description: Test

            ## Comando
            ```bash
            echo "test"
            ```
            """;

        Files.writeString(skillsDir.resolve("MY.md"), skillContent);

        SkillManager manager = new SkillManager(skillLoader, skillExecutor);

        assertTrue(manager.hasSkill("MySkill"));
        assertTrue(manager.hasSkill("myskill"));
        assertTrue(manager.hasSkill("MYSKILL"));

        assertNotNull(manager.getSkill("myskill"));
    }
}
