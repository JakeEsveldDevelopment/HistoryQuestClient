package com.historyquestwaifuedition.fragments


import android.app.Dialog
import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.historyquestwaifuedition.R
import com.historyquestwaifuedition.api.HistoryQuestApi
import com.historyquestwaifuedition.api.HistoryQuestApiService
import com.historyquestwaifuedition.math.IntVec2D
import com.historyquestwaifuedition.models.HMap
import com.historyquestwaifuedition.models.Node
import com.historyquestwaifuedition.models.NodeData
import com.historyquestwaifuedition.models.Player
import kotlinx.android.synthetic.main.fragment_game.*
import kotlinx.android.synthetic.main.hud.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class GameFragment : Fragment() {
    private lateinit var historyQuestApiService: HistoryQuestApiService
    private lateinit var player: Player
    private lateinit var map: HMap
    private lateinit var mapFragment: MapFragment
    private lateinit var HUDFragment: HUDFragment

    private var onReturnToMainMenuListener: OnReturnToMainMenuListener? = null

    private var getMapCall: Call<MutableList<NodeData>>? = null
    private var getMapServerCalls = 0
    private val getMapCallback: Callback<MutableList<NodeData>> = object: Callback<MutableList<NodeData>> {
        override fun onFailure(call: Call<MutableList<NodeData>>, t: Throwable) {
            // call server again because required data is missing
            getMap()
        }

        override fun onResponse(
            call: Call<MutableList<NodeData>>,
            response: Response<MutableList<NodeData>>
        ) {
            handleGetMap(response)

            // TODO get player
            t_loading.text = "Loading Player"
            mapFragment.setPlayer(player)
            fl_loading.visibility = View.GONE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        historyQuestApiService = HistoryQuestApi(context!!).service

        player = Player("testplayer", 100, IntVec2D(0, 0))
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_game, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mapFragment = childFragmentManager.findFragmentById(R.id.f_map) as MapFragment
        HUDFragment = childFragmentManager.findFragmentById(R.id.f_hud) as HUDFragment

        HUDFragment.right_button.setOnClickListener {
            moveRight()
        }

        HUDFragment.left_button.setOnClickListener {
            moveLeft()
        }

        HUDFragment.up_button.setOnClickListener {
            moveUp()
        }

        HUDFragment.down_button.setOnClickListener {
            moveDown()
        }

        HUDFragment.treasure.setOnClickListener{
            enterRoom()
        }

        object: Thread() {
            override fun run() {
                super.run()
                sleep(2000) // because map view is not created yet

                activity!!.runOnUiThread {
                    getMap()
                }
            }
        }.start()
    }

    private fun getMap() {
        if (getMapServerCalls++ <= MAX_SERVER_CALL) {
            getMapCall?.cancel()
            getMapCall = historyQuestApiService.getMap()
            t_loading.text = "Loading Map"
            getMapCall?.enqueue(getMapCallback)
        } else {
            // return to the main menu since we cannot get the proper data from the server
            onReturnToMainMenuListener?.onReturnToMainMenu()
        }
    }

    private fun handleGetMap(response: Response<MutableList<NodeData>>) {
        if (!response.isSuccessful && response.body() == null) {
            // call server again because required data is missing
            getMap()
            return
        }

        var hasFailed = false
        val size = IntVec2D(20, 20)
        val nodesTemp = mutableListOf<MutableList<Node?>>()
        for (x in 0 until size.x) {
            val ys = mutableListOf<Node?>()
            for (y in 0 until size.y) {
                ys.add(null)
            }
            nodesTemp.add(ys)
        }
        for (nodeData in response.body()!!) {

            if (nodeData.id == null || nodeData.name == null ||
                nodeData.positionx == null || nodeData.positiony == null) {
                hasFailed = true
                break
            }

            nodesTemp[nodeData.positionx!!][nodeData.positiony!!] = Node(
                nodeData.id!!,
                nodeData.name!!,
                nodeData.desc ?: "",
                IntVec2D(nodeData.positionx!!, nodeData.positiony!!)
            )
        }

        if (hasFailed) {
            // call server again because required data is missing
            getMap()
            return
        }

        map = HMap(size, nodesTemp as MutableList<MutableList<Node>>)
        mapFragment.setMap(map)
    }

    private fun moveRight() {
        if (player.position.x == map.size.x - 1) return // OOB
        player.position.x += 1
        mapFragment.updatePlayerPosition()
    }

    private fun moveLeft() {
        if (player.position.x == 0) return // OOB
        player.position.x -= 1
        mapFragment.updatePlayerPosition()
    }

    private fun moveUp() {
        if (player.position.y == 0) return // OOB
        player.position.y -= 1
        mapFragment.updatePlayerPosition()
    }

    private fun moveDown() {
        if (player.position.y == map.size.y - 1) return // OOB
        player.position.y += 1
        mapFragment.updatePlayerPosition()
    }

    private fun enterRoom(){
        val node = mapFragment.getLandmarkNode(player)

        if (node.name != "Road") {
            val dialogueFrag = DialogueInteraction.newInstance(player, node)

            childFragmentManager.beginTransaction()
                .add(R.id.fl_game_container, dialogueFrag)
                .addToBackStack(null)
                .commit()
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        if (context is OnReturnToMainMenuListener) {
            onReturnToMainMenuListener = context
        } else {
            throw RuntimeException("Context must implement OnReturnToMainMenuListener")
        }
    }

    override fun onDetach() {
        super.onDetach()

        onReturnToMainMenuListener = null
        getMapCall?.cancel()
    }

    companion object {
        private const val MAX_SERVER_CALL = 5

        @JvmStatic
        fun newInstance() =
            GameFragment()
    }

    interface OnReturnToMainMenuListener {
        fun onReturnToMainMenu()
    }
}
