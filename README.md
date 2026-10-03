# DuelliRealistici

Sistema di duelli realistici per server Minecraft (Paper/Spigot) 1.21+

## Caratteristiche

- **Mondi dedicati**: Teletrasporto automatico in un mondo separato per i duelli
- **Inventari salvati**: Salvataggio e ripristino automatico degli inventari pre-duello
- **Sistema Kit**: 3 kit preconfigurati (Guerriero, Arciere, Mago) con permessi dedicati
- **Statistiche MySQL**: Tracciamento completo (vittorie, sconfitte, pareggi, kill, death, streak, K/D, winrate)
- **Leaderboard**: Classifiche per vittorie, duelli totali, winrate, streak
- **Ricompense**: Denaro (Vault), comandi console, oggetti personalizzabili per vincitori/perdenti/pareggio
- **Cooldown**: Sistema cooldown configurabile tra un duello e l'altro
- **Protezioni**: Border arena, blocco comandi, protezione blocchi, anti-drop oggetti

## Comandi

| Comando | Descrizione | Permesso |
|---------|-------------|----------|
| `/duello <giocatore> [kit]` | Sfida un giocatore | `duelli.use` |
| `/duello accetta [giocatore]` | Accetta una sfida | `duelli.use` |
| `/duello rifiuta [giocatore]` | Rifiuta una sfida | `duelli.use` |
| `/duello annulla` | Annulla la tua sfida | `duelli.use` |
| `/duello kit [info <nome>]` | Lista/Info kit | `duelli.kit` |
| `/duello stats [giocatore]` | Statistiche duelli | `duelli.stats` |
| `/duello top [tipo] [pagina]` | Leaderboard | `duelli.top` |
| `/duelloadmin reload` | Ricarica config | `duelli.admin` |
| `/duelloadmin createworld` | Crea mondo duello | `duelli.admin` |
| `/duelloadmin setspawn <1\|2>` | Imposta spawn | `duelli.admin` |
| `/duelloadmin forcestop <p1> <p2>` | Forza fine duello | `duelli.admin` |
| `/duelloadmin list` | Lista duelli attivi | `duelli.admin` |

## Kit Disponibili

### Guerriero (`duelli.kit.guerriero`)
- Armadura di diamante completa (Protezione IV, Durabilità III)
- Spada di diamante (Affilatezza V, Durabilità III)
- Arco (Potenza III, Frecce Infinite, Durabilità III)
- 8 Mele d'oro, 32 Manzo cotto
- Effetti: Velocità I, Forza I

### Arciere (`duelli.kit.arciere`)
- Armadura di ferro (Protezione IV, Durabilità III, Furtività Veloce III)
- Arco (Potenza V, Frecce Infinite, Spinta II, Durabilità III)
- Balestra (Caricamento Rapido III, Multishot, Traforo II, Durabilità III)
- 64 Freccie, 16 Freccie Spettrali
- Spada di ferro, 5 Mele d'oro, 32 Pollo cotto
- Effetti: Velocità II, Visione Notturna

### Mago (`duelli.kit.mago`)
- Armadura di cuoio colorata (Protezione IV, Durabilità III)
- Bacchetta del Mago (Verga di Blaze)
- 8 Perle dell'Ender
- Pozioni splash: Danno II (8), Cura II (4), Velocità II (4), Forza II (4)
- 3 Mele d'oro, 1 Mela d'oro Incantata
- Effetti: Velocità I, Rigenerazione II

## Installazione

1. Scarica l'ultima release
2. Metti il `.jar` nella cartella `plugins/`
3. Riavvia il server
4. Configura `config.yml` (MySQL, ricompense, mondo duello)
5. Configura `kits.yml` se vuoi modificare i kit
6. Riavvia o usa `/duelloadmin reload`

## Configurazione MySQL

```yaml
mysql:
  enabled: true
  host: "localhost"
  port: 3306
  database: "coralmc_duelli"
  username: "root"
  password: ""
```

Il plugin creerà automaticamente le tabelle `duelli_stats` e `duelli_history`.

## Dipendenze

- **Paper/Spigot 1.21+** (richiesto)
- **Vault** (opzionale, per ricompense economiche)
- **MySQL/MariaDB** (richiesto per statistiche)

## Permessi

| Permesso | Default | Descrizione |
|----------|---------|-------------|
| `duelli.use` | true | Comandi base duelli |
| `duelli.stats` | true | Vedere proprie stats |
| `duelli.stats.others` | op | Vedere stats altrui |
| `duelli.top` | true | Vedere leaderboard |
| `duelli.kit` | true | Vedere kit |
| `duelli.kit.guerriero` | true | Usare kit Guerriero |
| `duelli.kit.arciere` | true | Usare kit Arciere |
| `duelli.kit.mago` | true | Usare kit Mago |
| `duelli.admin` | op | Comandi admin |
| `duelli.bypass` | op | Bypass cooldown/restrizioni |

## Build da sorgente

```bash
# Richiede Java 21 e Maven 3.9+
mvn clean package
```

Il file `.jar` sarà in `target/`.

## Supporto

Per segnalazioni bug o richieste feature, apri una issue su GitHub.