# TradeRealm - MMORPG Trading Simulator

A Java Swing marketplace: 10 bot players auction and trade items, outbidding each
other based on past prices, while random world events (dragon attack, gold rush,
market crash, ...) move the economy. Buy/sell order book with escrow, a 2% market
fee that burns gold, multiple account windows on one shared market, and an admin
dashboard. Group 28 OOP mini project.

## Run it

Works on JDK 8 and newer. Pick the block for your shell - the commands differ
because they build the list of source files differently.

Windows - cmd.exe, from the project folder:

    dir /s /b src\*.java > sources.txt    mkdir out
    javac -d out "@sources.txt"
    java -cp out mmo.market.Main

Windows - PowerShell, from the project folder:

    Get-ChildItem -Recurse -Filter *.java src | ForEach-Object { '"' + $_.FullName + '"' } | Out-File -Encoding ascii sources.txt
    mkdir out
    javac -d out "@sources.txt"
    java -cp out mmo.market.Main

    (Windows PowerShell 5.1 uses UTF-16 with plain ">". Use the explicit encoding above.)

Mac / Linux:

    find src -name '*.java' > sources.txt
    mkdir -p out
    javac -d out "@sources.txt"
    java -cp out mmo.market.Main

Use javac and java from the same JDK. If you move the project to another laptop,
compile it there again rather than copying the old out/ folder.
The cmd block is for pasting into cmd.exe, not a .bat file.

On first run the app creates a data/ folder (accounts, price catalog, live market)
next to where you run it. Those files are local only and not committed.

## Logins to try

- Register any account - you start with 1000 gold and a starter pack of items.
- admin / admin123 - seeded admin account, gets the Admin tab (all accounts,
  set catalog prices, trigger world events, spawn items, watch the fee counter).

## OOP concepts used

- Inheritance: Account -> Player -> BotPlayer and Player -> Admin; role is polymorphic getRole()
- Interfaces: Authenticatable, Storable
- Enums with fields and behaviour: Rarity, MarketEvent, Order.Side, TradeOffer.Status
- Custom exception hierarchy: AuthException, InvalidCredentialsException, UsernameTakenException,
  AuctionException, TradeException, OrderException, InsufficientFundsException
- Encapsulation: bids, trades and orders are escrowed and settle only through the Market class;
  the 2% market fee lives in one fee() helper (faucet/sink economics)
- Price-time priority order matching: getBestBuy / getBestSell
- Dependency injection: MarketSession owns the shared world, every window receives it
- Observer: windows register tick listeners on the shared BotEngine
- MVC: several MarketWindow views over one Market model; Admin tab exists only for Admin accounts
- File handling: accounts, item prices, the live market and the fee counter saved as text records
- Swing GUI: shared Theme class, CardLayout login/register, event engine on a 2-second timer
