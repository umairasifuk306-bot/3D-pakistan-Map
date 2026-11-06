package com.pakmap.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class InfoSection(val title: String, val rows: List<Pair<String, String>>)

object InfoData {
    val sections = listOf(
        InfoSection(
            "Basic Information",
            listOf(
                "Official name" to "Islamic Republic of Pakistan",
                "Capital" to "Islamabad",
                "Largest city" to "Karachi",
                "Independence" to "14 August 1947",
                "Area" to "About 881,913 km²",
                "Population" to "241.49 million (2023 Census)",
                "Currency" to "Pakistani Rupee (PKR)",
                "Languages" to "Urdu (national), English (official)",
                "Time zone" to "PKT (UTC+5)"
            )
        ),
        InfoSection(
            "Provinces and Capitals",
            listOf(
                "Punjab" to "Lahore",
                "Sindh" to "Karachi",
                "Khyber Pakhtunkhwa" to "Peshawar",
                "Balochistan" to "Quetta",
                "Islamabad Capital Territory" to "Islamabad",
                "Gilgit-Baltistan" to "Gilgit",
                "Azad Jammu and Kashmir" to "Muzaffarabad"
            )
        ),
        InfoSection(
            "Boundaries",
            listOf(
                "West / Northwest" to "Afghanistan",
                "West" to "Iran",
                "East" to "India",
                "Northeast" to "China",
                "South" to "Arabian Sea (coastline about 1,046 km)"
            )
        ),
        InfoSection(
            "National Symbols",
            listOf(
                "National flower" to "Jasmine",
                "National animal" to "Markhor",
                "National bird" to "Chukar",
                "National tree" to "Deodar",
                "National fruit" to "Mango",
                "National sport" to "Field hockey",
                "National poet" to "Allama Muhammad Iqbal",
                "Founder" to "Muhammad Ali Jinnah (Quaid-e-Azam)"
            )
        ),
        InfoSection(
            "National Anthem",
            listOf(
                "Name" to "Qaumi Tarana",
                "Lyrics" to "Hafeez Jalandhari",
                "Music" to "Ahmed G. Chagla",
                "Official adoption" to "1954"
            )
        ),
        InfoSection(
            "Constitution and Government",
            listOf(
                "First constitution" to "1956",
                "Second constitution" to "1962",
                "Current constitution" to "1973 (in force from 14 August 1973)",
                "18th Amendment" to "2010; gave more powers to the provinces",
                "Parliament" to "National Assembly and Senate"
            )
        ),
        InfoSection(
            "Judiciary",
            listOf(
                "Highest court" to "Supreme Court of Pakistan (Islamabad)",
                "High Courts" to "One in each province and Islamabad",
                "Other" to "Federal Shariat Court"
            )
        ),
        InfoSection(
            "Important Days",
            listOf(
                "5 February" to "Kashmir Solidarity Day",
                "23 March" to "Pakistan Day",
                "1 May" to "Labour Day",
                "28 May" to "Youm-e-Takbeer",
                "14 August" to "Independence Day",
                "6 September" to "Defence Day",
                "9 November" to "Iqbal Day",
                "25 December" to "Quaid-e-Azam Day"
            )
        ),
        InfoSection(
            "Wars and Conflicts",
            listOf(
                "1947-48" to "First Kashmir war",
                "1965" to "Indo-Pak war",
                "1971" to "War; East Pakistan became Bangladesh",
                "1999" to "Kargil conflict"
            )
        ),
        InfoSection(
            "Military Operations and HQ",
            listOf(
                "Rah-e-Rast" to "2009, Swat",
                "Zarb-e-Azb" to "2014, North Waziristan",
                "Radd-ul-Fasaad" to "2017, nationwide",
                "GHQ" to "Rawalpindi",
                "Air HQ and Naval HQ" to "Islamabad"
            )
        ),
        InfoSection(
            "Nuclear Information",
            listOf(
                "Nuclear tests" to "28 and 30 May 1998 (Chagai, Balochistan)",
                "Youm-e-Takbeer" to "28 May",
                "First nuclear power plant" to "KANUPP, Karachi (1972)",
                "Atomic energy body" to "PAEC (established 1956)"
            )
        ),
        InfoSection(
            "Awards",
            listOf(
                "Nishan-e-Pakistan" to "Highest civil award",
                "Hilal-e-Imtiaz" to "Civil award",
                "Sitara-e-Imtiaz" to "Civil award",
                "Tamgha-e-Imtiaz" to "Civil award",
                "Nishan-e-Haider" to "Highest military gallantry award; first recipient Capt. Raja Muhammad Sarwar (1948)"
            )
        ),
        InfoSection(
            "Firsts",
            listOf(
                "First Governor-General" to "Muhammad Ali Jinnah",
                "First Prime Minister" to "Liaquat Ali Khan",
                "First President" to "Iskander Mirza (1956)",
                "First woman Prime Minister" to "Benazir Bhutto (1988)",
                "First Nobel Prize" to "Abdus Salam, Physics (1979)",
                "Youngest Nobel laureate" to "Malala Yousafzai, Peace (2014)",
                "First motorway" to "M-2, Lahore-Islamabad (1997)"
            )
        ),
        InfoSection(
            "Largest, Longest, Highest",
            listOf(
                "Highest peak" to "K2 (8,611 m)",
                "Longest river" to "Indus",
                "Largest city" to "Karachi",
                "Largest province (area)" to "Balochistan",
                "Largest province (population)" to "Punjab",
                "Smallest province (area)" to "Khyber Pakhtunkhwa",
                "Largest dam" to "Tarbela (largest earth-filled dam in the world)",
                "Longest tunnel" to "Lowari Tunnel (about 8.5 km)"
            )
        ),
        InfoSection(
            "Banks",
            listOf(
                "Central bank" to "State Bank of Pakistan (established 1 July 1948)",
                "Major banks" to "National Bank of Pakistan, Habib Bank Limited"
            )
        ),
        InfoSection(
            "Census",
            listOf(
                "2017 Census" to "207.68 million",
                "2023 Census" to "241.49 million (first digital census)"
            )
        )
    )
}

@Composable
fun InfoScreen(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val green = Color(0xFF01411C)

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF1F4F1)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(green)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Pakistan Information",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(InfoData.sections) { section ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                section.title,
                                color = green,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            for ((label, value) in section.rows) {
                                Column(modifier = Modifier.padding(top = 10.dp)) {
                                    Text(
                                        label,
                                        color = Color.Gray,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    Text(value, color = Color.Black)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
