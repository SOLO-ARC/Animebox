package com.lagradost.cloudstream3.ui.animebox.games

data class AnimeMobileGame(
    val title: String,
    val packageName: String,
    val franchise: String,
    val logoUrl: String,
    val playStoreUrl: String = "https://play.google.com/store/apps/details?id=$packageName"
)

object AnimeGamesCatalog {
    val GAMES = listOf(
        AnimeMobileGame(
            title = "Genshin Impact",
            packageName = "com.miHoYo.GenshinImpact",
            franchise = "Genshin Impact",
            logoUrl = "https://play-lh.googleusercontent.com/YQqyKaXX-63krqsfIzUEJWUWLINxcb5tbS6QVySdxbS7eZV7YB2dUjUvX27xA0TIGtfxQ5v-tQjwlT5tTB-O=w512-h512-rw"
        ),
        AnimeMobileGame(
            title = "Honkai: Star Rail",
            packageName = "com.HoYoverse.hkrpgoversea",
            franchise = "Honkai",
            logoUrl = "https://play-lh.googleusercontent.com/aWrGocSA7hEuk1qAPe7L4T57LvLKrwwH26cK2_LOqxRQMQX7j3uHYojC-EKWgYEV2PdrmE0ahqvvhLhXrAGk6Q=w512-h512-rw"
        ),
        AnimeMobileGame(
            title = "Zenless Zone Zero",
            packageName = "com.HoYoverse.Nap",
            franchise = "Zenless Zone Zero",
            logoUrl = "https://play-lh.googleusercontent.com/N8j-t_IXvDvqFzN3U_19OJPIyAHHgljwVa7TMyCYpdRXRykalU3HKq9u9oPnzDXEkUOX1yyuX16yuWNDrUC_Uw=w512-h512-rw"
        ),
        AnimeMobileGame(
            title = "Fate/Grand Order",
            packageName = "com.aniplex.fategrandorder.en",
            franchise = "Fate",
            logoUrl = "https://play-lh.googleusercontent.com/EzN3-WzS_i1mEjUaAht6flnqKF1tNDUqPjeAOgn_ZC1YqG65s9kP84lSV7HHoVIemgpJKRca63jdsnPorCD-pQ=w512-h512-rw"
        ),
        AnimeMobileGame(
            title = "Dragon Ball Legends",
            packageName = "com.bandainamcoent.dblegends_ww",
            franchise = "Dragon Ball",
            logoUrl = "https://play-lh.googleusercontent.com/4wuf73Ss17wtmTUkacVxdSmWqyW0yjgPi2Bmzt2nuuSYuT7vX5cXZW69Cir4JRM0aKClpgmgMyHRPChiImUhOdQ=w512-h512-rw"
        ),
        AnimeMobileGame(
            title = "Bleach: Brave Souls",
            packageName = "com.klab.bleach",
            franchise = "Bleach",
            logoUrl = "https://play-lh.googleusercontent.com/l4q4CLhcfufU6l5qBxe7YM7i1TDULUsGeHTt0QFhazqrkZMtuG3sKf-jslNZu_dNK__FNGOAV0jl_jtPOVFLxQ=w512-h512-rw"
        ),
        AnimeMobileGame(
            title = "One Piece Bounty Rush",
            packageName = "com.bandainamcoent.opbrww",
            franchise = "One Piece",
            logoUrl = "https://play-lh.googleusercontent.com/RneoX1S2-RaQ9XkPswwIZtCk1pXBGQCppuWEfQ9NbzI-VEGkr0vp14LnfL7852DbBa2NxJBEZLMUqcP077_99DY=w512-h512-rw"
        ),
        AnimeMobileGame(
            title = "Solo Leveling: ARISE",
            packageName = "com.netmarble.sololv",
            franchise = "Solo Leveling",
            logoUrl = "https://play-lh.googleusercontent.com/MfVM3wvhQwzhPbwjzd5tiZQKR7EyCkZlNxG68d_4haoTAaZLT8A0GtdyeMXaMujZikQ8Nt7qP2rXol-k_zhulA=w512-h512-rw"
        ),
        AnimeMobileGame(
            title = "The Seven Deadly Sins: Grand Cross",
            packageName = "com.netmarble.nanagb",
            franchise = "Seven Deadly Sins",
            logoUrl = "https://play-lh.googleusercontent.com/tLE7G16BjgBRgcfSaHRjb0Yy_oKuy41jywfHv5bInR_MuxjyF6UhRmNUgwLRZ7trmQ3CWZM_48VaP0hf_f9d=w512-h512-rw"
        ),
        AnimeMobileGame(
            title = "Blue Archive",
            packageName = "com.nexon.bluearchive",
            franchise = "Blue Archive",
            logoUrl = "https://play-lh.googleusercontent.com/vlVAv0UGD-knTIap84gJDkKZlnZjzhgYdAU3W6mRVkzIjc-rxtRcy5EHud3Om2haqzqW9-73AjYnYiuSluy39S4=w512-h512-rw"
        ),
        AnimeMobileGame(
            title = "Wuthering Waves",
            packageName = "com.kurogame.wutheringwaves.global",
            franchise = "Wuthering Waves",
            logoUrl = "https://play-lh.googleusercontent.com/S4HjezIFmL17FpPu7Z8VzxZvFMABYUjfLkcrDSEKxpjCYIL3tWLQK76_16SDOTiZlVHm_KeoMawvC7BPcyW_=w512-h512-rw"
        ),
        AnimeMobileGame(
            title = "GODDESS OF VICTORY: NIKKE",
            packageName = "com.proximabeta.nikke",
            franchise = "Nikke",
            logoUrl = "https://play-lh.googleusercontent.com/JK5mejs0bVT_9613gCxNfCjRw_m3JiQ4wC8B8Kt471yu7ThFQazVkCLTfPZ81hnenft7ilaE-qSwVGpQT6sEFcY=w512-h512-rw"
        ),
        AnimeMobileGame(
            title = "Honkai Impact 3rd",
            packageName = "com.miHoYo.bh3oversea",
            franchise = "Honkai",
            logoUrl = "https://play-lh.googleusercontent.com/rKiMpbQqkg-LUolGjtRvi3T-SEVL30hY_2A1PWK0jagN380TUXj0SHQu9fkmiDdEAtA_J4SHW8p_czxpAAbyYw=w512-h512-rw"
        ),
        AnimeMobileGame(
            title = "Arknights",
            packageName = "com.YoStarEN.Arknights",
            franchise = "Arknights",
            logoUrl = "https://play-lh.googleusercontent.com/pBNlu-ZHQlVdfXhzI7TGIjqt_00sAeYFFK6AqTygfF3rwQfoPIkfIz-zROY31POTDKtxqcMDMPaDLJVlwCdc=w512-h512-rw"
        ),
        AnimeMobileGame(
            title = "Azur Lane",
            packageName = "com.YoStarEN.AzurLane",
            franchise = "Azur Lane",
            logoUrl = "https://play-lh.googleusercontent.com/3Yg8H1F9bsSTJI4-DQhJJYYokT3kEUaNG5h2JBP4Qxbzter1Q9iu3nsvepVZzMpX-VpSEgZc-UbS34TwBybQsWg=w512-h512-rw"
        ),
        AnimeMobileGame(
            title = "Sword Art Online: Integral Factor",
            packageName = "com.bandainamcoent.saoif",
            franchise = "Sword Art Online",
            logoUrl = "https://play-lh.googleusercontent.com/hSDuOgcuxw4Qe-TW3fEPYKNPZ-yPsjMexUh_Dfuk0OcQvAFDibRVQBXr4HXVD7kkiH9DmZEXn2E7X-eFe7C3FA=w512-h512-rw"
        ),
        AnimeMobileGame(
            title = "Yu-Gi-Oh! MASTER DUEL",
            packageName = "jp.konami.masterduel",
            franchise = "Yu-Gi-Oh!",
            logoUrl = "https://play-lh.googleusercontent.com/iXWTX-D0Ehok3_lsmw-Bf0ow69CqUBsFv7u_zy8r-SOQML6e4KfOk7CYLutfvSwi-WLUvuctzvJQPKh1NjVb6g=w512-h512-rw"
        ),
        AnimeMobileGame(
            title = "Yu-Gi-Oh! Duel Links",
            packageName = "jp.konami.duellinks",
            franchise = "Yu-Gi-Oh!",
            logoUrl = "https://play-lh.googleusercontent.com/_iCy5x5sSZpkzAcPlTxNN8RlPYGrwZ255QxZx6TuVd41-nlZ1PSrNYB5mDLAqBUtxCiDOhY_x31t6pd_bKQFbrk=w512-h512-rw"
        ),
        AnimeMobileGame(
            title = "Epic Seven",
            packageName = "com.stove.epic7.google",
            franchise = "Epic Seven",
            logoUrl = "https://play-lh.googleusercontent.com/r3Q3Q8qy8C2SoUUF59o8NMHyE3lTGh3TuUw4XsJVCs5L1tpkRPWp__TKYth0oqr5enygDUi3LrHq1030Rifw5w=w512-h512-rw"
        ),
        AnimeMobileGame(
            title = "DRAGON BALL Z DOKKAN BATTLE",
            packageName = "com.bandainamcogames.dbzdokkanww",
            franchise = "Dragon Ball",
            logoUrl = "https://play-lh.googleusercontent.com/oFUkc2wlYMqyU6ExDA41mlij85yrtHyhUSKKADdfLcMjX6nG-cKJURbDD_RrVBX3VROgxmfmljuk-IeHKcacAg=w512-h512-rw"
        ),
        AnimeMobileGame(
            title = "ONE PIECE TREASURE CRUISE-RPG",
            packageName = "com.namcobandaigames.spmoja010E",
            franchise = "One Piece",
            logoUrl = "https://play-lh.googleusercontent.com/QQ8TSmUGfxGnu34f1z0V1r_Xfcd_i6u5A7dYrYF1qtkqyaRU56xp1aiMIPuFYGC2bvXt7pfAOUIoUfw6H-EG=w512-h512-rw"
        ),
        AnimeMobileGame(
            title = "NARUTO: Ultimate Ninja STORM",
            packageName = "com.bandainamcoent.ultimateninjastorm",
            franchise = "Naruto",
            logoUrl = "https://play-lh.googleusercontent.com/11bPEEUpln4WxSmP8mCFQz7isXWCzeSIcVbO_cAnWa3Ze3skO0U_EiPQdDbs2llL5I4S6MZTSbLEH_iAiDi_=w512-h512-rw"
        ),
        AnimeMobileGame(
            title = "The Eminence in Shadow RPG",
            packageName = "com.crunchyroll.eminenceinshadowrpg",
            franchise = "Crunchyroll",
            logoUrl = "https://play-lh.googleusercontent.com/ZR58Ivi3_SP5jz3I9A_mfGVCiQPt6bSlrjmZobpSp3Gz1f0WAP38QAdFoi6T73I5fM9UN0ixt2q74RJeLkgDE9A=w512-h512-rw"
        ),
        AnimeMobileGame(
            title = "Punishing: Gray Raven",
            packageName = "com.kurogame.gplay.punishing.grayraven.en",
            franchise = "Punishing Gray Raven",
            logoUrl = "https://play-lh.googleusercontent.com/YWC3LT98lQ2INdjSE-zEEorezgBedT0N5G1lc8QEX-qpUqqJ2zOEnFOSWYQp8NBGwpo6Yp3HAgkwU8fWBW24DA=w512-h512-rw"
        ),
        AnimeMobileGame(
            title = "Jujutsu Kaisen Phantom Parade",
            packageName = "com.bilibilihk.jujutsuphanparagp",
            franchise = "Jujutsu Kaisen",
            logoUrl = "https://play-lh.googleusercontent.com/_3JFCRzaFgARkff2H4q_1FH4qusQsEyeBVo_uRUrK7gpPKFON8_VJqXc5IpJQ0ho57StJ4Z6AFqGsrlO7fnhIVw=w512-h512-rw"
        ),
        AnimeMobileGame(
            title = "SAO Integral Factor - MMORPG",
            packageName = "com.bandainamcoent.saoifww",
            franchise = "Sword Art Online",
            logoUrl = "https://play-lh.googleusercontent.com/6XsjBymOKDDbYfYXbnhh1b1HdPEvG9tMNMVPSFK9JJGmr8o5OabH_58QSNpGDMXEVsl9oMErUj9T2Ftz9DWZkg=w512-h512-rw"
        ),
        AnimeMobileGame(
            title = "Pokémon UNITE",
            packageName = "jp.pokemon.pokemonunite",
            franchise = "Pokémon",
            logoUrl = "https://play-lh.googleusercontent.com/Nk76reilDJP3THrTFjD3uJSU9rV8kGt4fa-g3nBjVjjzV4qHHhGlELCN6G6Vm4hQdbgr2J7UVcPVxRVujWKJ=w512-h512-rw"
        ),
        AnimeMobileGame(
            title = "Pokémon GO",
            packageName = "com.nianticlabs.pokemongo",
            franchise = "Pokémon",
            logoUrl = "https://play-lh.googleusercontent.com/DUA40mf0fd6lUkU_Pvfw78BOoY37nPNM5tKw0tf4sCqzJfNwR84wqj-J00WN99QaZ89SpruNsU1xX7sDw6uBZg=w512-h512-rw"
        ),
        AnimeMobileGame(
            title = "Pokémon Sleep",
            packageName = "jp.pokemon.pokemonsleep",
            franchise = "Pokémon",
            logoUrl = "https://play-lh.googleusercontent.com/tg-BeEJO7UDjvfxWXWJ1u-PMLa-45twu0YhJkZfQu-PEjRJU_2l9Sb9xMR3YBha3mtLzdzU-016ZpWN5IUwrFwU=w512-h512-rw"
        ),
        AnimeMobileGame(
            title = "Pokémon Masters EX",
            packageName = "com.dena.a12026418",
            franchise = "Pokémon",
            logoUrl = "https://play-lh.googleusercontent.com/IsrKHHbA8zI-ek18iMBN1yCBqCocyGVX_0dT-Q4uRRF1pnlaqhn-_E1KjJ0u5nPcXyprqprFlgq7ijx5qy5i=w512-h512-rw"
        ),
        AnimeMobileGame(
            title = "Pokémon Café ReMix",
            packageName = "jp.pokemon.pokemoncafemix",
            franchise = "Pokémon",
            logoUrl = "https://play-lh.googleusercontent.com/UtRqNNsRk7FH2WhnIpSSKx7K_M0eCicQpwUFac4_fqgfP49kyIqQ5c0skGm3oHKbY3preqglQVRDc43JTPF73us=w512-h512-rw"
        ),
        AnimeMobileGame(
            title = "Pokémon TCG Live",
            packageName = "com.pokemon.pokemontcgl",
            franchise = "Pokémon",
            logoUrl = "https://play-lh.googleusercontent.com/03__y7JK4iuWR1R2sa5cI4s7wUur04Sg_hlztwylEvgbIIifWjkgrPlx-i-WZ_PnW8dqYsSfocQk6PuGU-6UPbk=w512-h512-rw"
        ),
        AnimeMobileGame(
            title = "Bloodline: The Last Royal Vampire",
            packageName = "com.crunchyroll.bloodlinegame",
            franchise = "Crunchyroll",
            logoUrl = "https://play-lh.googleusercontent.com/uJ1viwkRDI1YD7dM-fg4KTxxDPXcnjiGm5AetlCke--PhuvBkw1mMaKEw_rOt6lO0MLDNpERFqoy9N54PnKo=w512-h512-rw"
        ),
        AnimeMobileGame(
            title = "Crunchyroll: Corpse Party",
            packageName = "com.crunchyroll.gv.corpseparty.game",
            franchise = "Corpse Party",
            logoUrl = "https://play-lh.googleusercontent.com/qrV80LjXZ13I-buEgHOYb2kHjuHrIkeyy2BinPhVqQm7J4xNqr1UXoVgs67rL_aAOTiwxGSKLCvhfdt47zCpusc=w512-h512-rw"
        ),
        AnimeMobileGame(
            title = "Captain Tsubasa: Ace",
            packageName = "com.dgames.g65002005.google",
            franchise = "Captain Tsubasa",
            logoUrl = "https://play-lh.googleusercontent.com/H4yzwXgQOWbg1SyfclqgfdY2fPoPV524m_NtCnyP4NxnVQ2p3R1Bd7CubLAqj6D-_xWfYf-up9k7gTK1Pw9h=w512-h512-rw"
        ),
        AnimeMobileGame(
            title = "Ni no Kuni: Cross Worlds",
            packageName = "com.netmarble.enn",
            franchise = "Level-5",
            logoUrl = "https://play-lh.googleusercontent.com/eyREl29W_9xiGGSh1IInIHHKNfHUoo7aJ1epmvuTQvYtmr4fF6bd6m6ZxRe9_eDxm6LWLhsudOwVT0thsP6aRA=w512-h512-rw"
        ),
        AnimeMobileGame(
            title = "Layton: Curious Village in HD",
            packageName = "com.Level5.LT1REU",
            franchise = "Level-5",
            logoUrl = "https://play-lh.googleusercontent.com/7G_Qbixbbph-nmqQwBJcu9vEHpoaZYh8WwCpCrpN4RcrJ9_RPP8da5rK5NeZc8YgexxTjm4Qql70jdwCbuLCbg=w512-h512-rw"
        ),
        AnimeMobileGame(
            title = "Layton: Diabolical Box in HD",
            packageName = "com.Level5.LT2REU",
            franchise = "Level-5",
            logoUrl = "https://play-lh.googleusercontent.com/PwUYZxywdi-49kpnAWIgL3PbX1Py00gl-u0wfJ7Th65xjbFmPJAMvKUlDOhyC_owUCMmxM_mplezeYRcNf8HIQ=w512-h512-rw"
        ),
        AnimeMobileGame(
            title = "Layton: Unwound Future in HD",
            packageName = "com.Level5.LT3REU",
            franchise = "Level-5",
            logoUrl = "https://play-lh.googleusercontent.com/Bs0hy3exr3qI3ISh8aayRYgkV3wckW-kO6cFesoIxM32SPOjVd1naSRv1JdUNST2g_l3DWSCEN_bdiWD9x3Xcw=w512-h512-rw"
        ),
        AnimeMobileGame(
            title = "Yo-kai Watch PuniPuni",
            packageName = "com.Level5.YWP",
            franchise = "Level-5",
            logoUrl = "https://play-lh.googleusercontent.com/tRm2I16Uuhcidsm3HukpW-1E61VBea_f9cv32tqro8m-k8BeiEdKBvfECb4ENyz-lNtiH37WaPDFkaRKbYvBzA=w512-h512-rw"
        ),
        AnimeMobileGame(
            title = "Neverness to Everness (NTE)",
            packageName = "com.hottagames.nte",
            franchise = "Hotta Studio",
            logoUrl = "https://play-lh.googleusercontent.com/TGl3DdDbi4hty6ooOhEvv_2s5tkpGLbL6derP8y5hK-_Bw--Ve375b08ba9HHQ9ueRpoQ9oye1T_T24BJriLUmw=w512-h512-rw"
        ),
        AnimeMobileGame(
            title = "Blue Protocol",
            packageName = "sea.haoplay.game.gp.bpsr",
            franchise = "Bandai Namco",
            logoUrl = "https://play-lh.googleusercontent.com/wnssr6kEkOai2r4iN2pN9xa5DOZk-Ud8NBbvfCh3JSxhbWWuLX40KPOfAuP9KMjLfZ7K2h-N81waCqDnjsEx=w512-h512-rw"
        ),
        AnimeMobileGame(
            title = "Ananta (Project Mugen)",
            packageName = "com.netease.anantana",
            franchise = "NetEase",
            logoUrl = "https://play-lh.googleusercontent.com/VV52RLZ43ZGnMvBL0KVrpgSCb-n5hWEamMmBMLVd3VCK1VwGQCPGQe7XxdM_srQclSxdUVRC4sQKBzXZwch5Yg=w512-h512-rw"
        ),
        AnimeMobileGame(
            title = "Arknights: Endfield",
            packageName = "com.gryphline.endfield.gp",
            franchise = "Gryphline",
            logoUrl = "https://play-lh.googleusercontent.com/l6FVNa293RykBWy88TqEhUakIcGSC8bRygSnKOBgztln48JX-WzMWnrBAETrKZsxDNC4HhwCsvfle_UI7rBE=w512-h512-rw"
        ),
        AnimeMobileGame(
            title = "Duet Night Abyss",
            packageName = "com.panstudio.gplay.duetnightabyss.arpg.global",
            franchise = "Hero Games",
            logoUrl = "https://play-lh.googleusercontent.com/wNyltcXoUMKz7txb1KGz0MN4-B-HXv6-iVC6n39cry5FNukJxtoJyQ-sC-KMrg-CNO1a_Ed3r8AaUJNuHWGsjA=w512-h512-rw"
        ),
        AnimeMobileGame(
            title = "Shin chan: Summer Vacation",
            packageName = "com.crunchyroll.gv.shinchansummervacation.game",
            franchise = "Shin-chan",
            logoUrl = "https://play-lh.googleusercontent.com/2vnuIOjJhqQ4uhGiAxLd_EDIelHIMWt0v44BqVbC8WLBRXns4HXKOEZA00x2w17LKJuBCuxfRd8vNzQtNYsQLw=w512-h512-rw"
        ),
        AnimeMobileGame(
            title = "Shin chan: Shiro & Coal Town",
            packageName = "com.crunchyroll.gv.shinchanshiroandcoal.game",
            franchise = "Shin-chan",
            logoUrl = "https://play-lh.googleusercontent.com/VX-ozKDCmtdcWlsx97NDau1aflEazk3YSd_L3ETSJVI1XYJXhW-u5aXqHRTxYN8FrztlW0ZkRI1KGnqlZZT5eAQ=w512-h512-rw"
        ),
        AnimeMobileGame(
            title = "FANTASY LIFE i: The Girl Who Steals Time",
            packageName = "jp.co.level5.fli",
            franchise = "Level-5",
            logoUrl = "https://play-lh.googleusercontent.com/rWYp14J2-cMWzhGRkxSyWhEBiiENYTUH28ADhx3_X1IlIC_3nDyOcJaQ5OJv3t4n5XERK0yBxc3keGKSitkqIQ=w512-h512-rw"
        ),
    )
}
