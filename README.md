# NotasWorkflow Android

Aplicativo Android, em Jetpack Compose, com as mesmas funções do complemento [Notas Workflow NVDA](https://github.com/weliveiracruz/NotasWorkflowNVDA) **1.12.0**: folhas (anotações e tarefas) em raias de status (A fazer, Em andamento, Bloqueada, Concluída), agrupadas em cadernos e pastas, com conteúdo formatado, salvamento automático, busca, folha a partir de documento e exportação como documento. Feito para funcionar por completo com o TalkBack, com o toque e com teclado físico.

## Funções (as mesmas do complemento 1.12.0)

| Complemento NVDA | Android |
|---|---|
| Janela principal: Mostrar em árvore, raias fechadas, 200 folhas por vez e "Mostrar mais" | Tela principal: botão **Mostrar** (árvore com pastas fechadas), raias como títulos que abrem e fecham, 200 folhas por vez |
| Alt+Shift+Setas (status), Ctrl+Shift+Setas (ordem na raia), Delete | Menu de ações do TalkBack e toque longo: Próximo status, Status anterior, Subir/Descer na raia, Mover para…, Exportar…, Excluir. Com teclado físico, os mesmos atalhos |
| Criar… > Folha, Caderno, Pasta, Folha a partir de documento | Botão **Criar…** com os mesmos itens; Nova folha tem **Novo caderno…** e **Nova pasta…**; Novo caderno tem **Nova pasta…** |
| Janela da folha: título, local, status, conteúdo, salvamento automático (2 s), Ctrl+S/B/I, Formatação…, Inserir documento… | Janela da folha igual, com **Situação do salvamento**; fechar salva; ao mudar status ou local, o foco vai para a próxima folha da raia |
| Marcação leve (## título, listas, citação, divisor, negrito, itálico, tachado, código) | A mesma marcação, convertida para o mesmo HTML guardado |
| Busca sem acentos e sem maiúsculas, com trecho, relevância e data | Tela **Buscar** com os mesmos resultados (mesmo algoritmo) |
| Cadernos e pastas: Criar, Renomear (F2), Mover para, Excluir (Delete) com contagem; excluir pasta/caderno exclui o conteúdo (1.12.0) | Tela **Cadernos e pastas** com as mesmas ações e as mesmas perguntas |
| Folha a partir de documento: .docx, .pptx, .txt, .html, .eml; limite de 1 MB por folha | Igual (leitor próprio de Word, PowerPoint, HTML e e-mail) |
| Exportar: Word, Markdown, HTML; um arquivo ou um por folha | Igual, gravando onde a pessoa escolher (seletor de arquivos do Android) |
| Pasta das folhas (OneDrive/Google Drive), Importar e Exportar folhas, gravação segura com cópia `.bak` | **Arquivo das folhas**: pode ser o próprio `NotasWorkflowNVDA-data.json` do complemento no Google Drive ou OneDrive, para ter as mesmas folhas no celular e no computador |
| Manual de uso | **Manual de uso…** dentro do aplicativo, com títulos navegáveis |

O formato do arquivo é o mesmo do complemento e do aplicativo web (`notas-workflow`, versão 1).

## Visual

O visual segue o Trello: cores, tipografia, espaçamento, cantos e sombras em tokens (`ui/Theme.kt`) com a paleta do Atlassian Design System, em tema claro e escuro. O fundo azul é o quadro, as raias são listas cinzas e as folhas são cartões brancos com a etiqueta da cor da raia. Os ícones são Material Symbols Outlined (de linha, como os do Trello). Na parte de baixo ficam as abas **Folhas**, **Buscar**, **Cadernos e pastas** e **Mais**. A tela principal tem o botão **Nova folha** e cada raia aberta termina com **Adicionar uma folha**.

## Estrutura

- `app/src/main/java/.../core`: o núcleo em Kotlin puro (sem Android), tradução direta dos módulos do complemento: `Model`, `NotesStore` (gravação em segundo plano, cópia de segurança, recuperação de arquivo danificado, importação), `Markup`, `Search`, `Importer` + `Mime`, `Exporter`, `Texts`.
- `app/src/main/java/.../android/Saf.kt`: arquivos escolhidos pela pessoa (Storage Access Framework).
- `app/src/main/java/.../ui`: as telas em Compose.
- `app/src/main/assets/manual.md`: o manual de uso.

## Testes

```
./gradlew testDebugUnitTest
```

- `CoreTest`: modelo, JSON, armazenamento, marcação, busca (inclusive 100 mil folhas), importação e exportação.
- `GoldenTest`: **paridade com o complemento 1.12.0**. Os resultados esperados em `app/src/test/resources/golden` foram gerados pelo próprio código Python do complemento (markup, search, exporter e importer), e o núcleo Kotlin precisa dar exatamente o mesmo.

## Gerar o APK

O GitHub Actions (`.github/workflows/android.yml`) roda os testes e gera o APK a cada envio; o APK fica nos artefatos da execução. Uma tag `v*` publica o APK numa versão do GitHub. Localmente:

```
./gradlew assembleDebug
```

Para assinar a versão de lançamento com uma chave própria, defina `NW_KEYSTORE_FILE`, `NW_KEYSTORE_PASSWORD`, `NW_KEY_ALIAS` e `NW_KEY_PASSWORD`; sem elas, a chave de depuração assina.

## Privacidade

O aplicativo não pede permissão de internet: nenhuma folha sai do aparelho, a não ser para os arquivos que a pessoa escolher.

## Licença

GNU General Public License, versão 2 (a mesma do complemento).
