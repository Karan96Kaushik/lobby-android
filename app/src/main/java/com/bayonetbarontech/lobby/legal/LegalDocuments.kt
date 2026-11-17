package com.bayonetbarontech.lobby.legal

enum class LegalKind {
    PRIVACY,
    TERMS
}

data class LegalSection(val heading: String, val paragraphs: List<String>)

object LegalDocuments {
    const val DEVELOPER = "Bayonet Baron Tech"
    const val APP = "Lobby"
    const val EFFECTIVE = "17 November 2026"

    fun title(kind: LegalKind): String = when (kind) {
        LegalKind.PRIVACY -> "Privacy policy"
        LegalKind.TERMS -> "Terms and conditions"
    }

    fun sections(kind: LegalKind): List<LegalSection> = when (kind) {
        LegalKind.PRIVACY -> privacy
        LegalKind.TERMS -> terms
    }

    private val privacy = listOf(
        LegalSection(
            "Who we are",
            listOf(
                "$APP is published by $DEVELOPER, a developer in India. This policy explains what the app does with information when you use it on a phone, tablet, or TV.",
                "Effective date: $EFFECTIVE."
            )
        ),
        LegalSection(
            "What this app is",
            listOf(
                "$APP opens websites you choose, starting with Internet Archive, inside the app. It can save a video for offline playback when you turn that on, and it can pair a phone with a TV on the same local network.",
                "$DEVELOPER does not run those websites and does not operate an account or cloud service for $APP."
            )
        ),
        LegalSection(
            "Information stored on your device",
            listOf(
                "The app keeps the following on the device, in app-private storage. It is not uploaded to $DEVELOPER:",
                "The source you last opened, sources you add, and whether offline download is on for each source.",
                "The last page opened for a source, so the app can restore it.",
                "Download records and the video files you choose to save.",
                "A pairing PIN, paired device names, and remote settings for the local remote.",
                "Website data that the page itself stores, such as cookies and cache, for sites you open.",
                "A cached copy of a source’s icon, fetched from that site."
            )
        ),
        LegalSection(
            "Information we do not collect",
            listOf(
                "$DEVELOPER does not ask you to create an account. The app does not include advertising or analytics tools operated by us, and we do not sell personal data.",
                "We do not receive your browsing history, downloads, or remote commands on a server of our own."
            )
        ),
        LegalSection(
            "Websites you open",
            listOf(
                "When a page loads, that website receives the connection from your device. That usually includes your IP address, a browser user-agent, and any cookies that site has already stored. Internet Archive and any other site you add have their own privacy policies.",
                "Those sites may be outside India. Their handling of information is not controlled by $DEVELOPER."
            )
        ),
        LegalSection(
            "Local remote",
            listOf(
                "Pairing works on the local network. The phone and TV exchange a PIN, device names, and remote commands directly with each other. Those messages are not sent to $DEVELOPER."
            )
        ),
        LegalSection(
            "Permissions",
            listOf(
                "Internet access is used to open pages and, if you allow it, to save a video.",
                "Network state and Wi-Fi permissions are used to tell when the device is online and to discover a paired Lobby device on the local network.",
                "Notifications are used only for the download notification, and only if you allow them.",
                "The app does not request camera, microphone, location, or shared-storage access. Pages are not given those capabilities."
            )
        ),
        LegalSection(
            "Children",
            listOf(
                "$APP is a general-purpose viewer and is not directed at children. Under the Digital Personal Data Protection Act, 2023, a child is a person under 18. If you allow a child to use a device with $APP installed, you are responsible for that use."
            )
        ),
        LegalSection(
            "How long information stays",
            listOf(
                "On-device information stays until you delete a download, clear browsing data in the app, clear the app’s storage, or uninstall $APP. Uninstalling removes the app’s private files from the device."
            )
        ),
        LegalSection(
            "Your choices",
            listOf(
                "You can decline notification permission, leave offline downloads off, and avoid pairing a remote. You can clear cookies, saved pages, and site data from Protection. You can remove a source you added and delete a saved video.",
                "Because $DEVELOPER does not hold a copy of this information, requests to access, correct, or erase it are handled on the device by those controls. If you still want to contact us, use the address below."
            )
        ),
        LegalSection(
            "Security",
            listOf(
                "App-private storage is protected by the device’s own account. No method of storage is perfectly secure. Keep the device locked and do not enter passwords or payment details in this viewer."
            )
        ),
        LegalSection(
            "Changes",
            listOf(
                "If this policy changes, the updated text will appear in the app with a new effective date. Continuing to use $APP after the update means you accept the updated policy."
            )
        ),
        LegalSection(
            "Contact and grievances",
            listOf(
                "Contact $DEVELOPER at the email address shown on the Google Play listing for $APP.",
                "If you have a grievance about personal data, write to that address with a short description. We will acknowledge the grievance and aim to resolve it within 15 days.",
                "This policy is meant to be read with the Digital Personal Data Protection Act, 2023, the rules made under it, and the Information Technology Act, 2000, to the extent they apply."
            )
        ),
        LegalSection(
            "Governing law",
            listOf(
                "This policy is governed by the laws of India. The courts in India have jurisdiction over disputes relating to it."
            )
        )
    )

    private val terms = listOf(
        LegalSection(
            "Agreement",
            listOf(
                "These terms are between you and $DEVELOPER, India, for the $APP app. By installing or using $APP you agree to these terms and to the privacy policy.",
                "Effective date: $EFFECTIVE. If you do not agree, do not use the app and uninstall it."
            )
        ),
        LegalSection(
            "The app",
            listOf(
                "$APP lets you open a chosen website, play that site’s own video, optionally save an HLS video for offline playback, and control another $APP device on the same local network.",
                "The built-in source is Internet Archive. You may add other web addresses. $DEVELOPER does not host the pages or videos those sites provide, and may change, suspend, or stop the app at any time."
            )
        ),
        LegalSection(
            "Who may use it",
            listOf(
                "You must be at least 18 years old, or use the app with permission from a parent or legal guardian. You are responsible for activity on devices you control."
            )
        ),
        LegalSection(
            "Acceptable use",
            listOf(
                "Use $APP only in line with these terms, the rules of each website you open, and applicable law in India and in the place you use the app.",
                "Do not use the app to break into systems, distribute malware, interfere with a site, or infringe someone else’s copyright or other rights. Do not attempt to bypass a site’s access controls, copy protection, or terms.",
                "You are responsible for sources you add and for deciding whether a download is allowed."
            )
        ),
        LegalSection(
            "Third-party sites",
            listOf(
                "Pages, players, and files come from third parties such as Internet Archive. Their terms and privacy policies apply as well. $DEVELOPER does not warrant that a site will be available, accurate, lawful, or safe.",
                "Links and playback inside a site are provided by that site. A complaint about a video or page should be directed to the site that hosts it."
            )
        ),
        LegalSection(
            "Offline copies",
            listOf(
                "Offline save is off until you turn it on for a source. A saved file is stored only on your device for your personal playback in $APP. You must have the right to make that copy. Remove it if you no longer have that right or if you uninstall the app."
            )
        ),
        LegalSection(
            "Remote control",
            listOf(
                "The remote feature pairs devices you control on a local network. Do not share a pairing PIN with someone you do not trust. You are responsible for commands sent from a device you paired."
            )
        ),
        LegalSection(
            "Intellectual property",
            listOf(
                "$DEVELOPER owns the app itself, including its name, code, and icon, except for third-party components and any marks that belong to their owners. Internet Archive and other sites own their own names, pages, and media.",
                "These terms give you a personal, non-exclusive, non-transferable licence to use the app. They do not give you any right in a third-party website or video."
            )
        ),
        LegalSection(
            "Disclaimer",
            listOf(
                "The app is provided “as is” and “as available”. To the extent permitted by Indian law, $DEVELOPER disclaims warranties of merchantability, fitness for a particular purpose, quiet enjoyment, and non-infringement.",
                "Nothing in these terms excludes a right or remedy that the Consumer Protection Act, 2019, or another Indian law does not allow to be excluded."
            )
        ),
        LegalSection(
            "Liability",
            listOf(
                "To the extent permitted by Indian law, $DEVELOPER is not liable for loss of data, loss of profits, or indirect or consequential loss, or for the content, availability, or legality of a third-party site.",
                "If $DEVELOPER is liable for a claim relating to the app, the total liability is limited to one thousand Indian rupees, except where the law does not allow that limit."
            )
        ),
        LegalSection(
            "Your responsibility",
            listOf(
                "You will bear claims, losses, and reasonable expenses that arise from your misuse of the app, a source you add, a copy you save without the right to do so, or your breach of these terms, except to the extent caused by $DEVELOPER’s own fault."
            )
        ),
        LegalSection(
            "Changes and ending use",
            listOf(
                "We may update these terms by publishing a new version in the app. The new effective date will be shown at the top. If you keep using $APP after that date, the new terms apply.",
                "You may stop using $APP at any time by uninstalling it. We may stop offering the app, or refuse a use that breaks these terms."
            )
        ),
        LegalSection(
            "Governing law",
            listOf(
                "These terms are governed by the laws of India. The courts in India have exclusive jurisdiction, except that you may also use any dispute forum that Indian consumer law requires to remain available to you.",
                "If a court holds part of these terms unenforceable, the rest stays in effect."
            )
        ),
        LegalSection(
            "Contact",
            listOf(
                "Contact $DEVELOPER at the email address shown on the Google Play listing for $APP."
            )
        )
    )
}
