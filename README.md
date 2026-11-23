# TradeRealm - MMORPG Trading Simulator

A Java Swing marketplace: 10 bot players auction and trade items, outbidding each
other based on past prices, while random world events (dragon attack, gold rush,
market crash, ...) move the economy. Group 28 OOP mini project.

## Run it

Needs any JDK 8 or newer.

Windows (cmd), from the project folder:

    dir /s /b src\*.java > sources.txt
    javac --release 8 -d out @sources.txt
    java -cp out mmo.market.Main

Mac/Linux:

    find src -name "*.java" > sources.txt
    javac --release 8 -d out @sources.txt
    java -cp out mmo.market.Main

On first run the app creates a data/ folder (accounts, price catalog, live market)
next to where you run it. Those files are local only and not committed.

## OOP concepts used

- Inheritance: Account -> Player -> BotPlayer; table cell renderers extend DefaultTableCellRenderer
- Interfaces: Authenticatable, Storable
- Enums with fields and behaviour: Rarity, MarketEvent, TradeOffer.Status
- Custom exception hierarchy: AuthException, InvalidCredentialsException, UsernameTakenException,
  AuctionException, TradeException, InsufficientFundsException
- Encapsulation: bids and trades are escrowed and settle only through the Market class
- Polymorphism: JTable picks cell renderers by type; Account roles behave differently
- File handling: accounts, item prices and the live market are saved as text records
- Swing GUI: shared Theme class, CardLayout login/register, event engine on a 2-second timer

