package ie.wit.fragments


import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

import ie.wit.R
import ie.wit.main.DonationApp
import ie.wit.models.DonationModel
import ie.wit.utils.*
import kotlinx.android.synthetic.main.fragment_donate.*
import kotlinx.android.synthetic.main.fragment_donate.view.*
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info
import org.jetbrains.anko.toast
import java.lang.String.format
import java.util.HashMap


class DonateFragment : Fragment(), AnkoLogger {

    lateinit var app: DonationApp
    var totalDonated = 0
    lateinit var loader : AlertDialog
    lateinit var eventListener : ValueEventListener
    var favourite = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = activity?.application as DonationApp
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val root = inflater.inflate(R.layout.fragment_donate, container, false)
        loader = createLoader(activity!!)
        activity?.title = getString(R.string.action_donate)

        root.progressBar.max = 10000
        root.amountPicker.minValue = 1
        root.amountPicker.maxValue = 1000

        root.amountPicker.setOnValueChangedListener { _, _, newVal ->
            //Display the newly selected number to paymentAmount
            root.paymentAmount.setText("$newVal")
        }
        setButtonListener(root)
        setFavouriteListener(root)
        return root;
    }

    companion object {
        @JvmStatic
        fun newInstance() =
            DonateFragment().apply {
                arguments = Bundle().apply {}
            }
    }

    fun setButtonListener( layout: View) {
        layout.donateButton.setOnClickListener {
            val amount = if (layout.paymentAmount.text.isNotEmpty())
                layout.paymentAmount.text.toString().toInt() else layout.amountPicker.value
            if(totalDonated >= layout.progressBar.max)
                activity?.toast("Donate Amount Exceeded!")
            else {
                val paymentmethod = if(layout.paymentMethod.checkedRadioButtonId == R.id.Direct) "Direct" else "Paypal"
                writeNewDonation(DonationModel(paymenttype = paymentmethod, amount = amount,
                    profilepic = app.userImage.toString(),
                    isfavourite = favourite,
                    latitude = app.currentLocation.latitude,
                    longitude = app.currentLocation.longitude,
                    email = app.auth.currentUser?.email))
            }
        }
    }

    fun setFavouriteListener (layout: View) {
        layout.imagefavourite.setOnClickListener(object : View.OnClickListener {
            override fun onClick(view: View?) {
                if (!favourite) {
                    layout.imagefavourite.setImageResource(android.R.drawable.star_big_on)
                    favourite = true
                }
                else {
                    layout.imagefavourite.setImageResource(android.R.drawable.star_big_off)
                    favourite = false
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        getTotalDonated(app.auth.currentUser?.uid)
    }

    override fun onPause() {
        super.onPause()
        if(app.auth.uid != null)
            app.database.child("user-donations")
                    .child(app.auth.currentUser!!.uid)
                    .removeEventListener(eventListener)
    }

    fun writeNewDonation(donation: DonationModel) {
        // Create new donation at /donations & /donations/$uid
        showLoader(loader, "Adding Donation to Firebase")
        info("Firebase DB Reference : $app.database")
        val uid = app.auth.currentUser!!.uid
        val key = app.database.child("donations").push().key
        if (key == null) {
            info("Firebase Error : Key Empty")
            return
        }
        donation.uid = key
        val donationValues = donation.toMap()

        val childUpdates = HashMap<String, Any>()
        childUpdates["/donations/$key"] = donationValues
        childUpdates["/user-donations/$uid/$key"] = donationValues

        app.database.updateChildren(childUpdates)
        hideLoader(loader)
    }

    fun getTotalDonated(userId: String?) {
        eventListener = object : ValueEventListener {
            override fun onCancelled(error: DatabaseError) {
                info("Firebase Donation error : ${error.message}")
            }

            override fun onDataChange(snapshot: DataSnapshot) {
                totalDonated = 0
                val children = snapshot.children
                children.forEach {
                    val donation = it.getValue<DonationModel>(DonationModel::class.java)
                    totalDonated += donation!!.amount
                }
                progressBar.progress = totalDonated
                totalSoFar.text = format("$ $totalDonated")
            }
        }

        app.database.child("user-donations").child(userId!!)
            .addValueEventListener(eventListener)
    }
}
