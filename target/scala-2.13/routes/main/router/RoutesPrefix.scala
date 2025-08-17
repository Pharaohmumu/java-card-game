// @GENERATOR:play-routes-compiler
// @SOURCE:D:/test/group22_card_game_code/conf/routes
// @DATE:Mon Mar 17 22:35:27 CST 2025


package router {
  object RoutesPrefix {
    private var _prefix: String = "/"
    def setPrefix(p: String): Unit = {
      _prefix = p
    }
    def prefix: String = _prefix
    val byNamePrefix: Function0[String] = { () => prefix }
  }
}
