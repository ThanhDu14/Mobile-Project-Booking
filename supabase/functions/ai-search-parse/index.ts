import { handleAiSearch } from "./handler.ts";

Deno.serve((request) => handleAiSearch(request));
