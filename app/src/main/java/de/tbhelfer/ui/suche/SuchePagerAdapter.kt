package de.tbhelfer.ui.suche

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class SuchePagerAdapter(parent: Fragment) : FragmentStateAdapter(parent) {
    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> SucheFotosFragment()
            else -> SucheBtbFragment()
        }
    }
}
