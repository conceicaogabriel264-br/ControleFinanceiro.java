import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeMap;

/**
 * Controle Financeiro Pessoal - Projeto Integrador: Laboratório de Programação.
 * Autor: Gabriel Conceição - Universidade Santo Amaro (UNISA).
 *
 * Programa de console que permite registrar receitas e despesas, consultar o
 * saldo, listar lançamentos ordenados, buscar por descrição, remover
 * lançamentos e gerar um resumo por categoria. Os dados são gravados em um
 * arquivo CSV (financas.csv), de modo que permanecem entre as execuções.
 */
public class ControleFinanceiro {

    /** Formato de data usado na entrada, na exibição e no arquivo. */
    static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Tipos possíveis de lançamento. */
    enum Tipo { RECEITA, DESPESA }

    // ------------------------------------------------------------------
    // Classe que representa um lançamento financeiro
    // ------------------------------------------------------------------
    static class Transacao {
        private final int id;
        private final LocalDate data;
        private final Tipo tipo;
        private final String categoria;
        private final BigDecimal valor;
        private final String descricao;

        Transacao(int id, LocalDate data, Tipo tipo, String categoria, BigDecimal valor, String descricao) {
            this.id = id;
            this.data = data;
            this.tipo = tipo;
            this.categoria = categoria;
            this.valor = valor;
            this.descricao = descricao;
        }

        int getId() { return id; }
        LocalDate getData() { return data; }
        Tipo getTipo() { return tipo; }
        String getCategoria() { return categoria; }
        BigDecimal getValor() { return valor; }
        String getDescricao() { return descricao; }

        /** Valor com sinal: receitas positivas e despesas negativas. */
        BigDecimal getValorComSinal() {
            return tipo == Tipo.RECEITA ? valor : valor.negate();
        }

        /** Linha de texto para gravação em arquivo (campos separados por ponto e vírgula). */
        String paraLinhaCsv() {
            return id + ";" + data.format(FORMATO_DATA) + ";" + tipo + ";"
                    + categoria + ";" + valor.toPlainString() + ";" + descricao;
        }

        /** Reconstrói um lançamento a partir de uma linha do arquivo. */
        static Transacao deLinhaCsv(String linha) {
            String[] p = linha.split(";", 6);
            return new Transacao(
                    Integer.parseInt(p[0]),
                    LocalDate.parse(p[1], FORMATO_DATA),
                    Tipo.valueOf(p[2]),
                    p[3],
                    new BigDecimal(p[4]),
                    p[5]);
        }

        @Override
        public String toString() {
            return String.format("%-4d %-10s %-8s %-14s %12s  %s",
                    id, data.format(FORMATO_DATA), tipo, categoria, formatar(valorComSinalExibicao()), descricao);
        }

        private BigDecimal valorComSinalExibicao() { return getValorComSinal(); }
    }

    // ------------------------------------------------------------------
    // Repositório: guarda a lista em memória e persiste em arquivo CSV
    // ------------------------------------------------------------------
    static class Repositorio {
        private final List<Transacao> transacoes = new ArrayList<>();
        private final Path arquivo;
        private int proximoId = 1;

        Repositorio(Path arquivo) {
            this.arquivo = arquivo;
            carregar();
        }

        List<Transacao> todas() { return new ArrayList<>(transacoes); }

        /** Cria e armazena um novo lançamento. */
        Transacao adicionar(LocalDate data, Tipo tipo, String categoria, BigDecimal valor, String descricao) {
            Transacao t = new Transacao(proximoId++, data, tipo, categoria, valor, descricao);
            transacoes.add(t);
            salvar();
            return t;
        }

        /** Remove um lançamento pelo identificador. Retorna true se encontrou. */
        boolean remover(int id) {
            for (int i = 0; i < transacoes.size(); i++) {
                if (transacoes.get(i).getId() == id) {
                    transacoes.remove(i);
                    salvar();
                    return true;
                }
            }
            return false;
        }

        private void carregar() {
            if (!Files.exists(arquivo)) {
                return;
            }
            try {
                for (String linha : Files.readAllLines(arquivo, StandardCharsets.UTF_8)) {
                    if (linha.isBlank()) {
                        continue;
                    }
                    Transacao t = Transacao.deLinhaCsv(linha);
                    transacoes.add(t);
                    if (t.getId() >= proximoId) {
                        proximoId = t.getId() + 1;
                    }
                }
            } catch (IOException | RuntimeException e) {
                System.out.println("Aviso: não foi possível ler o arquivo de dados (" + e.getMessage() + ").");
            }
        }

        private void salvar() {
            List<String> linhas = new ArrayList<>();
            for (Transacao t : transacoes) {
                linhas.add(t.paraLinhaCsv());
            }
            try {
                Files.write(arquivo, linhas, StandardCharsets.UTF_8);
            } catch (IOException e) {
                System.out.println("Erro ao gravar o arquivo de dados: " + e.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------
    // Algoritmos
    // ------------------------------------------------------------------

    /** Calcula o saldo (receitas - despesas) de uma lista de lançamentos. */
    static BigDecimal calcularSaldo(List<Transacao> lista) {
        BigDecimal saldo = BigDecimal.ZERO;
        for (Transacao t : lista) {
            saldo = saldo.add(t.getValorComSinal());
        }
        return saldo;
    }

    /** Soma os valores de um determinado tipo de lançamento. */
    static BigDecimal somarPorTipo(List<Transacao> lista, Tipo tipo) {
        BigDecimal total = BigDecimal.ZERO;
        for (Transacao t : lista) {
            if (t.getTipo() == tipo) {
                total = total.add(t.getValor());
            }
        }
        return total;
    }

    /**
     * Ordenação por inserção (insertion sort) pelo valor, implementada
     * manualmente para praticar algoritmos de ordenação. Complexidade O(n²).
     */
    static List<Transacao> ordenarPorValor(List<Transacao> origem, boolean decrescente) {
        List<Transacao> v = new ArrayList<>(origem);
        for (int i = 1; i < v.size(); i++) {
            Transacao chave = v.get(i);
            int j = i - 1;
            while (j >= 0 && comparar(v.get(j), chave, decrescente) > 0) {
                v.set(j + 1, v.get(j));
                j--;
            }
            v.set(j + 1, chave);
        }
        return v;
    }

    private static int comparar(Transacao a, Transacao b, boolean decrescente) {
        int r = a.getValor().compareTo(b.getValor());
        return decrescente ? -r : r;
    }

    /** Ordena por data (do mais antigo para o mais recente) usando a biblioteca padrão. */
    static List<Transacao> ordenarPorData(List<Transacao> origem) {
        List<Transacao> v = new ArrayList<>(origem);
        v.sort((a, b) -> {
            int r = a.getData().compareTo(b.getData());
            return r != 0 ? r : Integer.compare(a.getId(), b.getId());
        });
        return v;
    }

    /** Busca sequencial por trecho da descrição, ignorando maiúsculas e minúsculas. */
    static List<Transacao> buscarPorDescricao(List<Transacao> lista, String trecho) {
        List<Transacao> achados = new ArrayList<>();
        String alvo = trecho.toLowerCase();
        for (Transacao t : lista) {
            if (t.getDescricao().toLowerCase().contains(alvo)) {
                achados.add(t);
            }
        }
        return achados;
    }

    /** Totaliza as despesas por categoria (mapa ordenado alfabeticamente). */
    static Map<String, BigDecimal> despesasPorCategoria(List<Transacao> lista) {
        Map<String, BigDecimal> mapa = new TreeMap<>();
        for (Transacao t : lista) {
            if (t.getTipo() == Tipo.DESPESA) {
                mapa.merge(t.getCategoria(), t.getValor(), BigDecimal::add);
            }
        }
        return mapa;
    }

    // ------------------------------------------------------------------
    // Utilidades de entrada e saída
    // ------------------------------------------------------------------

    /** Formata um valor em reais, no padrão brasileiro (ex.: -1.234,50). */
    static String formatar(BigDecimal v) {
        String s = v.setScale(2, RoundingMode.HALF_UP).abs().toPlainString();
        String[] partes = s.split("\\.");
        StringBuilder inteiro = new StringBuilder(partes[0]);
        for (int i = inteiro.length() - 3; i > 0; i -= 3) {
            inteiro.insert(i, '.');
        }
        String sinal = v.signum() < 0 ? "-" : "";
        return sinal + inteiro + "," + partes[1];
    }

    /** Converte texto digitado em BigDecimal aceitando vírgula ou ponto decimal. */
    static BigDecimal lerValor(String texto) {
        String limpo = texto.trim().replace("R$", "").replace(" ", "");
        if (limpo.contains(",")) {
            limpo = limpo.replace(".", "").replace(",", ".");
        }
        BigDecimal v = new BigDecimal(limpo);
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    static void imprimirCabecalho() {
        System.out.printf("%-4s %-10s %-8s %-14s %12s  %s%n", "ID", "DATA", "TIPO", "CATEGORIA", "VALOR (R$)", "DESCRIÇÃO");
        System.out.println("-".repeat(72));
    }

    static void imprimirLista(List<Transacao> lista) {
        if (lista.isEmpty()) {
            System.out.println("Nenhum lançamento encontrado.");
            return;
        }
        imprimirCabecalho();
        for (Transacao t : lista) {
            System.out.println(t);
        }
    }

    // ------------------------------------------------------------------
    // Interface de menu
    // ------------------------------------------------------------------

    static void cadastrar(Scanner in, Repositorio repo, Tipo tipo) {
        try {
            System.out.print("Data (dd/mm/aaaa) [Enter = hoje]: ");
            String d = in.nextLine().trim();
            LocalDate data = d.isEmpty() ? LocalDate.now() : LocalDate.parse(d, FORMATO_DATA);

            System.out.print("Valor (R$): ");
            BigDecimal valor = lerValor(in.nextLine());
            if (valor.signum() <= 0) {
                System.out.println("O valor deve ser maior que zero.");
                return;
            }

            System.out.print("Categoria (ex.: Salário, Moradia, Alimentação): ");
            String categoria = in.nextLine().trim().replace(";", ",");
            if (categoria.isEmpty()) {
                categoria = "Outros";
            }

            System.out.print("Descrição: ");
            String descricao = in.nextLine().trim().replace(";", ",");

            Transacao t = repo.adicionar(data, tipo, categoria, valor, descricao);
            System.out.println("Lançamento " + t.getId() + " registrado com sucesso.");
        } catch (DateTimeParseException e) {
            System.out.println("Data inválida. Use o formato dd/mm/aaaa.");
        } catch (NumberFormatException e) {
            System.out.println("Valor inválido. Digite apenas números, por exemplo 150,50.");
        }
    }

    static void mostrarSaldo(Repositorio repo) {
        List<Transacao> lista = repo.todas();
        System.out.println("Total de receitas: R$ " + formatar(somarPorTipo(lista, Tipo.RECEITA)));
        System.out.println("Total de despesas: R$ " + formatar(somarPorTipo(lista, Tipo.DESPESA)));
        System.out.println("Saldo atual:       R$ " + formatar(calcularSaldo(lista)));
    }

    static void mostrarResumo(Repositorio repo) {
        Map<String, BigDecimal> mapa = despesasPorCategoria(repo.todas());
        if (mapa.isEmpty()) {
            System.out.println("Não há despesas registradas.");
            return;
        }
        BigDecimal total = somarPorTipo(repo.todas(), Tipo.DESPESA);
        System.out.println("Despesas por categoria:");
        for (Map.Entry<String, BigDecimal> e : mapa.entrySet()) {
            BigDecimal pct = e.getValue().multiply(BigDecimal.valueOf(100))
                    .divide(total, 1, RoundingMode.HALF_UP);
            System.out.printf("  %-16s R$ %12s  (%s%%)%n", e.getKey(), formatar(e.getValue()), pct.toPlainString());
        }
        System.out.println("  " + "-".repeat(44));
        System.out.printf("  %-16s R$ %12s%n", "TOTAL", formatar(total));
    }

    static void menu() {
        System.out.println();
        System.out.println("===== CONTROLE FINANCEIRO PESSOAL =====");
        System.out.println("1 - Registrar receita");
        System.out.println("2 - Registrar despesa");
        System.out.println("3 - Listar lançamentos (por data)");
        System.out.println("4 - Listar por valor (maior para menor)");
        System.out.println("5 - Buscar por descrição");
        System.out.println("6 - Ver saldo");
        System.out.println("7 - Resumo de despesas por categoria");
        System.out.println("8 - Remover lançamento");
        System.out.println("0 - Sair");
        System.out.print("Escolha uma opção: ");
    }

    public static void main(String[] args) {
        Path arquivo = Paths.get(args.length > 0 ? args[0] : "financas.csv");
        Repositorio repo = new Repositorio(arquivo);
        Scanner in = new Scanner(System.in, StandardCharsets.UTF_8);

        int opcao = -1;
        while (opcao != 0) {
            menu();
            if (!in.hasNextLine()) {
                break; // fim da entrada (útil para testes automatizados)
            }
            String linha = in.nextLine().trim();
            try {
                opcao = Integer.parseInt(linha);
            } catch (NumberFormatException e) {
                opcao = -1;
            }

            switch (opcao) {
                case 1 -> cadastrar(in, repo, Tipo.RECEITA);
                case 2 -> cadastrar(in, repo, Tipo.DESPESA);
                case 3 -> imprimirLista(ordenarPorData(repo.todas()));
                case 4 -> imprimirLista(ordenarPorValor(repo.todas(), true));
                case 5 -> {
                    System.out.print("Trecho da descrição: ");
                    imprimirLista(buscarPorDescricao(repo.todas(), in.nextLine().trim()));
                }
                case 6 -> mostrarSaldo(repo);
                case 7 -> mostrarResumo(repo);
                case 8 -> {
                    System.out.print("ID do lançamento a remover: ");
                    try {
                        int id = Integer.parseInt(in.nextLine().trim());
                        System.out.println(repo.remover(id) ? "Lançamento removido." : "ID não encontrado.");
                    } catch (NumberFormatException e) {
                        System.out.println("ID inválido.");
                    }
                }
                case 0 -> System.out.println("Encerrando. Seus dados foram salvos em " + arquivo + ".");
                default -> System.out.println("Opção inválida.");
            }
        }
    }
}
