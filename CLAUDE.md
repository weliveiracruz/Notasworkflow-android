# Instruções para quem trabalha neste repositório (pessoas e agentes de IA)

O NotasWorkflow Android é a versão Android do complemento [NotasWorkflowNVDA](https://github.com/weliveiracruz/NotasWorkflowNVDA). A referência de funções é o complemento: a versão do aplicativo (`versionName` em `app/build.gradle.kts`) acompanha a versão do complemento cujas funções ele tem; o último número sobe para mudanças só do Android (1.12.1).

## Regras

* O núcleo (`app/src/main/java/.../core`) é Kotlin puro: nada de classes do Android ali, para ser testado na JVM.
* Toda mudança de comportamento do núcleo precisa de teste em `CoreTest`. Se o complemento mudar a marcação, a busca, a importação ou a exportação, gere de novo os vetores de `app/src/test/resources/golden` com o código Python do complemento e mantenha o `GoldenTest` passando.
* O formato do arquivo das folhas é o do complemento (`notas-workflow`, versão 1): nunca mude sem mudar o complemento junto.
* Acessibilidade primeiro: toda ação de uma lista precisa estar no menu de ações do TalkBack (`customActions`), no toque longo e, quando o complemento tem atalho, no teclado físico. Títulos de seção com `heading()`, escolhas como botões de opção, mensagens faladas pelo `vm.say`.
* Os textos da interface são em português do Brasil, com as mesmas palavras do complemento ("folha", "raia", "caderno", "pasta").
* Visual: as cores, a tipografia, o espaçamento, os cantos e as sombras vêm dos tokens de `ui/Theme.kt` (paleta do Trello / Atlassian Design System, tema claro e escuro). Nenhuma cor ou tamanho fixo nas telas; ícones sempre do estilo Outlined. Não usar o logotipo nem os ícones próprios do Trello.
* Nunca apagar folhas sem confirmação.
* Nenhuma permissão de internet.
* Atualize o manual (`app/src/main/assets/manual.md`) e o `CHANGELOG.md` a cada mudança de função.
* Indentação com tabulação.
