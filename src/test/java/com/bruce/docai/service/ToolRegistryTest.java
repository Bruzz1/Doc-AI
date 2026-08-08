package com.bruce.docai.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolRegistryTest {

    private static AgentTool tool(String name) {
        return () -> name;
    }

    @Test
    void blankConfigurationEnablesAllRegisteredTools() {
        AgentTool a = tool("rag_search");
        AgentTool b = tool("order_lookup");
        ToolRegistry registry = new ToolRegistry(List.of(a, b));

        assertEquals(2, registry.resolve(null).size());
        assertEquals(2, registry.resolve("   ").size());
        assertEquals(java.util.Set.of("rag_search", "order_lookup"), registry.availableToolNames());
    }

    @Test
    void resolvesOnlyRequestedToolsInOrderAndSkipsUnknown() {
        AgentTool a = tool("rag_search");
        AgentTool b = tool("order_lookup");
        ToolRegistry registry = new ToolRegistry(List.of(a, b));

        List<Object> resolved = registry.resolve(" order_lookup , nope , rag_search ");

        assertEquals(2, resolved.size());
        assertSame(b, resolved.get(0));
        assertSame(a, resolved.get(1));
    }

    @Test
    void duplicateNamesKeepFirstAndBlankNamesAreIgnored() {
        AgentTool first = tool("rag_search");
        AgentTool duplicate = tool("rag_search");
        AgentTool blank = tool("  ");
        ToolRegistry registry = new ToolRegistry(List.of(first, duplicate, blank));

        assertEquals(java.util.Set.of("rag_search"), registry.availableToolNames());
        List<Object> resolved = registry.resolve("rag_search");
        assertEquals(1, resolved.size());
        assertSame(first, resolved.get(0));
    }

    @Test
    void allUnknownNamesResolveToEmptyList() {
        ToolRegistry registry = new ToolRegistry(List.of(tool("rag_search")));
        assertTrue(registry.resolve("does_not_exist").isEmpty());
    }
}
