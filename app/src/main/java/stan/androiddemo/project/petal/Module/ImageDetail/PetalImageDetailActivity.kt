package stan.androiddemo.project.petal.Module.ImageDetail

import android.Manifest
import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.os.Environment
import android.support.design.widget.FloatingActionButton
import android.support.graphics.drawable.AnimatedVectorDrawableCompat
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.Target
import com.facebook.drawee.controller.BaseControllerListener
import com.facebook.imagepipeline.image.ImageInfo
import kotlinx.android.synthetic.main.activity_petal_image_detail.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import rx.Observable
import rx.Subscriber
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import stan.androiddemo.R
import stan.androiddemo.project.petal.API.OperateAPI
import stan.androiddemo.project.petal.Base.BasePetalActivity
import stan.androiddemo.project.petal.Config.Config
import stan.androiddemo.project.petal.Event.OnDialogInteractionListener
import stan.androiddemo.project.petal.Event.OnImageDetailFragmentInteractionListener
import stan.androiddemo.project.petal.HttpUtiles.RetrofitClient
import stan.androiddemo.project.petal.Model.PinsMainInfo
import stan.androiddemo.project.petal.Module.BoardDetail.BoardDetailActivity
import stan.androiddemo.project.petal.Module.UserInfo.PetalUserInfoActivity
import stan.androiddemo.project.petal.Observable.AnimatorOnSubscribe
import stan.androiddemo.project.petal.Widget.GatherDialogFragment
import stan.androiddemo.tool.AnimatorUtils
import stan.androiddemo.tool.CompatUtils
import stan.androiddemo.tool.ImageLoad.ImageLoadBuilder
import stan.androiddemo.tool.Logger
import stan.androiddemo.tool.SPUtils
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit

class PetalImageDetailActivity : BasePetalActivity(), OnDialogInteractionListener,OnImageDetailFragmentInteractionListener {



    private val KEYPARCELABLE = "Parcelable"
    private var mActionFrom: Int = 0


    lateinit var mPinsBean:PinsMainInfo

    lateinit var mImageUrl: String//图片地址
    lateinit var mImageType: String//图片类型
    lateinit var mPinsId: String

    private var isLike = false//该图片是否被喜欢操作 默认false 没有被操作过
    private var isGathered = false//该图片是否被采集过

    var arrBoardId:List<String>? = null

    lateinit var mDrawableCancel: Drawable
    lateinit var mDrawableRefresh: Drawable



    override fun getTag(): String {return this.toString() }

    companion object {

        val ACTION_KEY = "key"//key值
        val ACTION_DEFAULT = -1//默认值
        val ACTION_THIS = 0//来自自己的跳转
        val ACTION_MAIN = 1//来自主界面的跳转
        val ACTION_MODULE = 2//来自模块界面的跳转
        val ACTION_BOARD = 3//来自画板界面的跳转
        val ACTION_ATTENTION = 4//来自我的关注界面的跳转
        val ACTION_SEARCH = 5//来自搜索界面的跳转

        fun launch(activity:Activity){
            val intent = Intent(activity,PetalImageDetailActivity::class.java)
            activity.startActivity(intent)
        }
        fun launch(activity: Activity,action:Int){
            val intent = Intent(activity,PetalImageDetailActivity::class.java)
            intent.putExtra("action",action)
            activity.startActivity(intent)
        }
    }


    override fun getLayoutId(): Int {
        return R.layout.activity_petal_image_detail
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //注册eventbus
        setSupportActionBar(toolbar)
        title = ""
        toolbar.setNavigationOnClickListener { onBackPressed() }
        EventBus.getDefault().register(this)
        mActionFrom = intent.getIntExtra("action",ACTION_DEFAULT)
        recoverData(savedInstanceState)

        mDrawableCancel = CompatUtils.getTintDrawable(mContext,R.drawable.ic_cancel_black_24dp,Color.GRAY)
        mDrawableRefresh = CompatUtils.getTintDrawable(mContext,R.drawable.ic_refresh_black_24dp,Color.GRAY)

        mImageUrl = mPinsBean.file!!.key!!
        mImageType = mPinsBean.file!!.type!!
        mPinsId = mPinsBean.pin_id.toString()

        isLike = mPinsBean.liked

        img_image_detail_bg.aspectRatio = mPinsBean.imgRatio

        supportFragmentManager.beginTransaction().replace(R.id.frame_layout_petal_with_refresh,
                PetalImageDetailFragment.newInstance(mPinsId)).commit()
        addSubscription(getGatherInfo())
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        outState?.putParcelable(KEYPARCELABLE,mPinsBean)
    }

    fun recoverData(savedInstanceState:Bundle?){
        if (savedInstanceState != null){
            if (savedInstanceState!!.getParcelable<PinsMainInfo>(KEYPARCELABLE)!=null){
                mPinsBean = savedInstanceState!!.getParcelable<PinsMainInfo>(KEYPARCELABLE)
            }
        }
    }

    override fun initResAndListener() {
        super.initResAndListener()
        fab_image_detail.setImageResource(R.drawable.ic_camera_white_24dp)
        fab_image_detail.setOnClickListener {
            showGatherDialog()
        }
    }

    @Subscribe(sticky = true)
    fun onEventReceiveBean(bean: PinsMainInfo) {
        //接受EvenBus传过来的数据
        Logger.d(TAG + " receive bean")
        this.mPinsBean = bean
    }

    override fun onResume() {
        super.onResume()
        showImage()
    }

    fun showImage(){
        var objectAnimator: ObjectAnimator? = null
        if (mImageType.toLowerCase().contains("fig")){
            objectAnimator = AnimatorUtils.getRotationFS(fab_image_detail)
            objectAnimator?.start()
        }
        val url = String.format(mFormatImageUrlBig,mImageUrl)
        val url_low = String.format(mFormatImageGeneral,mImageUrl)
        ImageLoadBuilder.Start(mContext,img_image_detail_bg,url).setUrlLow(url_low)
                .setRetryImage(mDrawableRefresh)
                .setFailureImage(mDrawableCancel)
                .setControllerListener(object:BaseControllerListener<ImageInfo>(){
                    override fun onFinalImageSet(id: String?, imageInfo: ImageInfo?, animatable: Animatable?) {
                        super.onFinalImageSet(id, imageInfo, animatable)
                        Logger.d("onFinalImageSet"+Thread.currentThread().toString())
                        if (animatable != null){
                            animatable.start()
                        }
                        if (objectAnimator!= null && objectAnimator!!.isRunning){
                            img_image_detail_bg.postDelayed(Runnable {
                                objectAnimator?.cancel()
                            },600)
                        }
                    }

                    override fun onSubmit(id: String?, callerContext: Any?) {
                        super.onSubmit(id, callerContext)
                        Logger.d("onSubmit"+Thread.currentThread().toString())
                    }

                    override fun onFailure(id: String?, throwable: Throwable?) {
                        super.onFailure(id, throwable)
                        Logger.d(throwable.toString())
                    }
                }).build()
    }


    override fun onClickPinsItemImage(bean: PinsMainInfo, view: View) {
        PetalImageDetailActivity.launch(this@PetalImageDetailActivity,PetalImageDetailActivity.ACTION_THIS)
    }

    override fun onClickPinsItemText(bean: PinsMainInfo, view: View) {
        PetalImageDetailActivity.launch(this@PetalImageDetailActivity,PetalImageDetailActivity.ACTION_THIS)
    }

    override fun onClickBoardField(key: String, title: String) {
        BoardDetailActivity.launch(this, key, title)
    }

    override fun onClickUserField(key: String, title: String) {
        PetalUserInfoActivity.launch(this, key, title)
    }

    override fun onClickImageLink(link: String) {
        val uri = Uri.parse(link)
        val int = Intent(Intent.ACTION_VIEW,uri)
        if (int.resolveActivity(this@PetalImageDetailActivity.packageManager) != null){
            startActivity(int)
        }
        else{
            Logger.d("checkResolveIntent = null")
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.petal_image_detail_menu,menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
         setMenuIconLikeDynamic(menu?.findItem(R.id.action_like),isLike)
        return super.onPrepareOptionsMenu(menu)
    }


    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when(item!!.itemId){
            android.R.id.home->{
                //这里原代码有一个逻辑要处理
            }
            R.id.action_like->{
                actionLike(item)
            }
            R.id.action_download->{
                downloadItem()
            }
            R.id.action_gather->{
                showGatherDialog()
            }
        }
        return true
    }

    fun setMenuIconLikeDynamic(item:MenuItem?,like:Boolean){
        val drawableCompat = if (like) AnimatedVectorDrawableCompat.create(mContext
                ,R.drawable.drawable_animation_petal_favorite_undo) else  AnimatedVectorDrawableCompat.create(mContext
                ,R.drawable.drawable_animation_petal_favorite_do)
        item?.icon = drawableCompat
    }

    fun downloadItem(){
        if (ContextCompat.checkSelfPermission(this@PetalImageDetailActivity, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this@PetalImageDetailActivity, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),1)
            return
        }

        object: AsyncTask<String, Int, File?>(){
            override fun doInBackground(vararg p0: String?): File? {
                var file: File? = null
                try {
                    val url = String.format(mFormatImageUrlBig,mImageUrl)
                    val future = Glide.with(this@PetalImageDetailActivity).load(url).downloadOnly(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL)
                    file = future.get()
                    val pictureFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).absoluteFile
                    val fileDir = File(pictureFolder,"Petal")
                    if (!fileDir.exists()){
                        fileDir.mkdir()
                    }
                    val fileName = System.currentTimeMillis().toString() + ".jpg"
                    val destFile = File(fileDir,fileName)
                    file.copyTo(destFile)
                    sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,Uri.fromFile(File(destFile.path))))
                }
                catch (e:Exception){
                    e.printStackTrace()
                }
                return file
            }

            override fun onPreExecute() {
                Toast.makeText(this@PetalImageDetailActivity,"保存图片成功", Toast.LENGTH_LONG).show()
            }

        }.execute()
    }

    fun  actionLike(menu: MenuItem){
        if (!isLogin){
            toast("请先登录再操作")
            return
        }
         val operation = if (isLike) Config.OPERATEUNLIKE else Config.OPERATELIKE
         RetrofitClient.createService(OperateAPI::class.java).httpsLikeOperate(mAuthorization,mPinsId,operation)
                 .subscribeOn(Schedulers.io())
                 .delay(600, TimeUnit.MILLISECONDS)
                 .observeOn(AndroidSchedulers.mainThread())
                 .subscribe(object:Subscriber<LikePinsOperateBean>(){
                     override fun onStart() {
                         super.onStart()
                         menu.isEnabled = false
                         (menu.icon as AnimatedVectorDrawableCompat)?.start()
                     }
                     override fun onCompleted() {
                         menu.isEnabled = true
                     }

                     override fun onNext(t: LikePinsOperateBean?) {
                         isLike = !isLike
                         setMenuIconLikeDynamic(menu,isLike)
                     }

                     override fun onError(e: Throwable?) {
                         menu.isEnabled = true
                         checkException(e!!,appBarLayout_image_detail)
                     }

                 })
     }

    fun showGatherDialog(){
        if (!isLogin){
            toast("请先登录再操作")
            return
        }
        val arrayBoardTitle = SPUtils.get(mContext,Config.BOARDTILTARRAY,"") as String
        val boardId = SPUtils.get(mContext,Config.BOARDIDARRAY,"") as String
        val arr = arrayBoardTitle?.split(",")
        arrBoardId = boardId?.split(",")
        val fragment = GatherDialogFragment.create(mAuthorization,mPinsId,mPinsBean.raw_text!!, ArrayList(arr))
        fragment.show(supportFragmentManager,null)
    }


    fun getGatherInfo(): Subscription {
        return RetrofitClient.createService(OperateAPI::class.java).httpsGatherInfo(mAuthorization,mPinsId,true)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(object:Subscriber<GatherInfoBean>(){
                    override fun onNext(t: GatherInfoBean?) {
                        if (t?.exist_pin != null){
                            setFabDrawableAnimator(R.drawable.ic_done_white_24dp,fab_image_detail)
                            isGathered = !isGathered
                        }
                    }

                    override fun onError(e: Throwable?) {
                    }

                    override fun onCompleted() {
                    }

                })
    }

    override fun onDialogClick(option: Boolean, info: HashMap<String, Any>) {
        if (option){
            val desc = info["describe"] as String
            val position = info["position"] as Int
            val animation = AnimatorUtils.getRotationAD(fab_image_detail)

            Observable.create(AnimatorOnSubscribe(animation)).observeOn(Schedulers.io())
                    .subscribeOn(AndroidSchedulers.mainThread())
                    .flatMap {
                        RetrofitClient.createService(OperateAPI::class.java).httpsGatherPins(mAuthorization,arrBoardId!![position],desc,mPinsId)
                    }
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(object :Subscriber<GatherResultBean>(){
                        override fun onNext(t: GatherResultBean?) {
                            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
                        }

                        override fun onError(e: Throwable?) {
                            checkException(e!!,appBarLayout_image_detail)
                            setFabDrawableAnimator(R.drawable.ic_done_white_24dp,fab_image_detail)
                        }

                        override fun onCompleted() {
                            setFabDrawableAnimator(R.drawable.ic_report_white_24dp,fab_image_detail)
                            isGathered = !isGathered
                        }

                    })
        }
    }

    fun setFabDrawableAnimator(resId:Int,mFabActionBtn:FloatingActionButton){
        mFabActionBtn.hide(object:FloatingActionButton.OnVisibilityChangedListener(){
            override fun onHidden(fab: FloatingActionButton?) {
                super.onHidden(fab)
                fab?.setImageResource(resId)
                fab?.show()
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)
    }

}
